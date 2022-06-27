/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.inlong.manager.service.core.sink;

import org.apache.inlong.manager.common.consts.InlongConstants;
import org.apache.inlong.manager.common.enums.SinkType;
import org.apache.inlong.manager.common.pojo.sink.SinkRequest;
import org.apache.inlong.manager.common.pojo.sink.StreamSink;
import org.apache.inlong.manager.common.pojo.sink.iceberg.IcebergSink;
import org.apache.inlong.manager.common.pojo.sink.iceberg.IcebergSinkRequest;
import org.apache.inlong.manager.service.ServiceBaseTest;
import org.apache.inlong.manager.service.core.impl.InlongStreamServiceTest;
import org.apache.inlong.manager.service.sink.StreamSinkService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Iceberg stream sink service test..
 */
public class IcebergStreamSinkServiceTest extends ServiceBaseTest {

    private final String globalGroupId = "b_group1";
    private final String globalStreamId = "stream1_iceberg";
    private final String globalOperator = "admin";
    // private final String sinkName = "default";

    @Autowired
    private StreamSinkService sinkService;
    @Autowired
    private InlongStreamServiceTest streamServiceTest;

    /**
     * Save sink info.
     */
    public Integer saveSink(String sinkName) {
        streamServiceTest.saveInlongStream(globalGroupId, globalStreamId, globalOperator);
        IcebergSinkRequest sinkInfo = new IcebergSinkRequest();
        sinkInfo.setInlongGroupId(globalGroupId);
        sinkInfo.setInlongStreamId(globalStreamId);
        sinkInfo.setSinkType(SinkType.SINK_ICEBERG);
        sinkInfo.setEnableCreateResource(InlongConstants.DISABLE_CREATE_RESOURCE);
        sinkInfo.setDataPath("hdfs://127.0.0.1:8020/data");
        sinkInfo.setSinkName(sinkName);
        sinkInfo.setId((int) (Math.random() * 100000 + 1));
        return sinkService.save(sinkInfo, globalOperator);
    }

    @Test
    public void testSaveAndDelete() {
        Integer id = this.saveSink("default1");
        Assertions.assertNotNull(id);
        boolean result = sinkService.delete(id, globalOperator);
        Assertions.assertTrue(result);
    }

    @Test
    public void testListByIdentifier() {
        Integer id = this.saveSink("default2");
        StreamSink sink = sinkService.get(id);
        Assertions.assertEquals(globalGroupId, sink.getInlongGroupId());
        sinkService.delete(id, globalOperator);
    }

    @Test
    public void testGetAndUpdate() {
        Integer id = this.saveSink("default3");
        StreamSink response = sinkService.get(id);
        Assertions.assertEquals(globalGroupId, response.getInlongGroupId());

        IcebergSink icebergSink = (IcebergSink) response;
        icebergSink.setEnableCreateResource(InlongConstants.DISABLE_CREATE_RESOURCE);
        SinkRequest request = icebergSink.genSinkRequest();
        boolean result = sinkService.update(request, globalOperator);
        Assertions.assertTrue(result);
    }
}
