/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.inlong.sort.standalone.sink.elasticsearch;

import java.util.concurrent.LinkedBlockingQueue;

import org.apache.inlong.commons.config.metrics.MetricRegister;
import org.apache.inlong.sort.standalone.channel.ProfileEvent;
import org.elasticsearch.action.DocWriteRequest.OpType;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

/**
 * 
 * TestEsCallbackListener
 */
@RunWith(PowerMockRunner.class)
@PowerMockIgnore("javax.management.*")
@PrepareForTest({EsSinkFactory.class, MetricRegister.class})
public class TestEsCallbackListener {

    private EsSinkContext context;

    @Before
    public void before() throws Exception {
        LinkedBlockingQueue<EsIndexRequest> dispatchQueue = new LinkedBlockingQueue<>();
        this.context = TestEsSinkContext.mock(dispatchQueue);
    }

    /**
     * testBeforeBulk
     */
    @Test
    public void testBeforeBulk() {
        // prepare
        ProfileEvent event = TestEsSinkContext.mockProfileEvent();
        EsIndexRequest indexRequest = context.getIndexRequestHandler().parse(context, event);
        long executionId = 0;
        BulkRequest bulkRequest = new BulkRequest();
        bulkRequest.add(indexRequest);
        // test
        EsCallbackListener listener = new EsCallbackListener(context);
        listener.beforeBulk(executionId, bulkRequest);
    }

    /**
     * testAfterSuccessBulk
     * 
     * @throws Exception
     */
    @Test
    public void testAfterSuccessBulk() throws Exception {
        // prepare
        LinkedBlockingQueue<EsIndexRequest> dispatchQueue = new LinkedBlockingQueue<>();
        EsSinkContext context = TestEsSinkContext.mock(dispatchQueue);
        ProfileEvent event = TestEsSinkContext.mockProfileEvent();
        EsIndexRequest indexRequest = context.getIndexRequestHandler().parse(context, event);
        long executionId = 0;
        EsCallbackListener listener = new EsCallbackListener(context);
        // request
        BulkRequest bulkRequest = new BulkRequest();
        bulkRequest.add(indexRequest);
        // success response
        IndexResponse indexResponse = new IndexResponse();
        BulkItemResponse itemResponse = new BulkItemResponse(0, OpType.INDEX, indexResponse);
        BulkItemResponse[] responses = new BulkItemResponse[1];
        responses[0] = itemResponse;
        BulkResponse bulkResponse = new BulkResponse(responses, 0);
        listener.afterBulk(executionId, bulkRequest, bulkResponse);

        // fail resend
        BulkItemResponse.Failure failure = new BulkItemResponse.Failure("index", OpType.INDEX.name(), "id",
                new Exception());
        BulkItemResponse failResponse = new BulkItemResponse(0, OpType.INDEX, failure);
        responses[0] = failResponse;
        listener.afterBulk(executionId, bulkRequest, bulkResponse);

        // failNull noResend
        BulkItemResponse.Failure failureNull = new BulkItemResponse.Failure("index", OpType.INDEX.name(), "id",
                null);
        BulkItemResponse failNullResponse = new BulkItemResponse(0, OpType.INDEX, failureNull);
        responses[0] = failNullResponse;
        listener.afterBulk(executionId, bulkRequest, bulkResponse);
    }
}
