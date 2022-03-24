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

package org.apache.inlong.manager.client.api.impl;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.inlong.manager.client.api.InlongStream;
import org.apache.inlong.manager.client.api.InlongStreamBuilder;
import org.apache.inlong.manager.client.api.InlongStreamConf;
import org.apache.inlong.manager.client.api.StreamField;
import org.apache.inlong.manager.client.api.StreamSink;
import org.apache.inlong.manager.client.api.StreamSource;
import org.apache.inlong.manager.client.api.inner.InnerGroupContext;
import org.apache.inlong.manager.client.api.inner.InnerInlongManagerClient;
import org.apache.inlong.manager.client.api.inner.InnerStreamContext;
import org.apache.inlong.manager.client.api.util.GsonUtil;
import org.apache.inlong.manager.client.api.util.InlongStreamSinkTransfer;
import org.apache.inlong.manager.client.api.util.InlongStreamSourceTransfer;
import org.apache.inlong.manager.client.api.util.InlongStreamTransfer;
import org.apache.inlong.manager.common.enums.SinkType;
import org.apache.inlong.manager.common.enums.SourceType;
import org.apache.inlong.manager.common.pojo.sink.SinkListResponse;
import org.apache.inlong.manager.common.pojo.sink.SinkRequest;
import org.apache.inlong.manager.common.pojo.source.SourceListResponse;
import org.apache.inlong.manager.common.pojo.source.SourceRequest;
import org.apache.inlong.manager.common.pojo.stream.InlongStreamFieldInfo;
import org.apache.inlong.manager.common.pojo.stream.InlongStreamResponse;

import java.util.List;

public class DefaultInlongStreamBuilder extends InlongStreamBuilder {

    private InlongStreamImpl inlongStream;

    private InlongStreamConf streamConf;

    private InnerGroupContext groupContext;

    private InnerStreamContext streamContext;

    private InnerInlongManagerClient managerClient;

    public DefaultInlongStreamBuilder(
            InlongStreamConf streamConf,
            InnerGroupContext groupContext,
            InnerInlongManagerClient managerClient) {
        this.streamConf = streamConf;
        this.groupContext = groupContext;
        this.managerClient = managerClient;
        if (MapUtils.isEmpty(groupContext.getStreamContextMap())) {
            groupContext.setStreamContextMap(Maps.newHashMap());
        }
        InlongStreamResponse stream = InlongStreamTransfer.createStreamInfo(streamConf, groupContext.getGroupInfo());
        InnerStreamContext streamContext = new InnerStreamContext(stream);
        groupContext.setStreamContext(streamContext);
        this.streamContext = streamContext;
        this.inlongStream = new InlongStreamImpl(stream.getName());
        if (CollectionUtils.isNotEmpty(streamConf.getStreamFields())) {
            this.inlongStream.setStreamFields(streamConf.getStreamFields());
        }
        groupContext.setStream(this.inlongStream);
    }

    @Override
    public InlongStreamBuilder source(StreamSource source) {
        inlongStream.addSource(source);
        SourceRequest sourceRequest = InlongStreamSourceTransfer.createSourceRequest(source,
                streamContext.getStreamInfo());
        streamContext.setSourceRequest(sourceRequest);
        return this;
    }

    @Override
    public InlongStreamBuilder sink(StreamSink sink) {
        inlongStream.addSink(sink);
        SinkRequest sinkRequest = InlongStreamSinkTransfer.createSinkRequest(sink, streamContext.getStreamInfo());
        streamContext.setSinkRequest(sinkRequest);
        return this;
    }

    @Override
    public InlongStreamBuilder fields(List<StreamField> fieldList) {
        inlongStream.setStreamFields(fieldList);
        List<InlongStreamFieldInfo> streamFieldInfos = InlongStreamTransfer.createStreamFields(fieldList,
                streamContext.getStreamInfo());
        streamContext.updateStreamFields(streamFieldInfos);
        return this;
    }

    @Override
    public InlongStream init() {
        InlongStreamResponse streamInfo = streamContext.getStreamInfo();
        String streamIndex = managerClient.createStreamInfo(streamInfo);
        streamInfo.setId(Double.valueOf(streamIndex).intValue());
        //Create source and update index
        List<SourceRequest> sourceRequests = Lists.newArrayList(streamContext.getSourceRequests().values());
        for (SourceRequest sourceRequest : sourceRequests) {
            String sourceIndex = managerClient.createSource(sourceRequest);
            sourceRequest.setId(Double.valueOf(sourceIndex).intValue());
        }
        //Create sink and update index
        List<SinkRequest> sinkRequests = Lists.newArrayList(streamContext.getSinkRequests().values());
        for (SinkRequest sinkRequest : sinkRequests) {
            String sinkIndex = managerClient.createSink(sinkRequest);
            sinkRequest.setId(Double.valueOf(sinkIndex).intValue());
        }
        return inlongStream;
    }

    @Override
    public InlongStream initOrUpdate() {
        InlongStreamResponse dataStreamInfo = streamContext.getStreamInfo();
        Pair<Boolean, InlongStreamResponse> existMsg = managerClient.isStreamExists(dataStreamInfo);
        if (existMsg.getKey()) {
            Pair<Boolean, String> updateMsg = managerClient.updateStreamInfo(dataStreamInfo);
            if (updateMsg.getKey()) {
                List<SourceRequest> sourceRequests = Lists.newArrayList(streamContext.getSourceRequests().values());
                for (SourceRequest sourceRequest : sourceRequests) {
                    sourceRequest.setId(initOrUpdateSource(sourceRequest));
                }
                List<SinkRequest> sinkRequests = Lists.newArrayList(streamContext.getSinkRequests().values());
                for (SinkRequest sinkRequest : sinkRequests) {
                    sinkRequest.setId(initOrUpdateSink(sinkRequest));
                }
            } else {
                throw new RuntimeException(String.format("Update data stream failed:%s", updateMsg.getValue()));
            }
            return inlongStream;
        } else {
            return init();
        }
    }

    private int initOrUpdateSource(SourceRequest sourceRequest) {
        String sourceType = sourceRequest.getSourceType();
        if (SourceType.KAFKA.name().equals(sourceType) || SourceType.BINLOG.name().equals(sourceType)) {
            List<SourceListResponse> responses = managerClient.listSources(sourceRequest.getInlongGroupId(),
                    sourceRequest.getInlongStreamId(), sourceRequest.getSourceType());
            if (CollectionUtils.isEmpty(responses)) {
                String sourceIndex = managerClient.createSource(sourceRequest);
                return Double.valueOf(sourceIndex).intValue();
            } else {
                SourceListResponse sourceListResponse = null;
                for (SourceListResponse response : responses) {
                    if (response.getSourceName().equals(sourceRequest.getSourceName())) {
                        sourceListResponse = response;
                        break;
                    }
                }
                if (sourceListResponse == null) {
                    String sourceIndex = managerClient.createSource(sourceRequest);
                    return Double.valueOf(sourceIndex).intValue();
                }
                sourceRequest.setId(sourceListResponse.getId());
                Pair<Boolean, String> updateMsg = managerClient.updateSource(sourceRequest);
                if (updateMsg.getKey()) {
                    return sourceListResponse.getId();
                } else {
                    throw new RuntimeException(
                            String.format("Update source:%s failed with ex:%s", GsonUtil.toJson(sourceRequest),
                                    updateMsg.getValue()));
                }
            }
        } else {
            throw new IllegalArgumentException(String.format("Unsupported source type:%s", sourceType));
        }
    }

    private int initOrUpdateSink(SinkRequest sinkRequest) {
        String sinkType = sinkRequest.getSinkType();
        boolean flag = SinkType.HIVE.name().equals(sinkType) || SinkType.KAFKA.name().equals(sinkType)
                || SinkType.CLICKHOUSE.name().equals(sinkType);
        if (flag) {
            List<SinkListResponse> responses = managerClient.listSinks(sinkRequest.getInlongGroupId(),
                    sinkRequest.getInlongStreamId(), sinkRequest.getSinkType());
            if (CollectionUtils.isEmpty(responses)) {
                String sinkIndex = managerClient.createSink(sinkRequest);
                return Double.valueOf(sinkIndex).intValue();
            } else {
                SinkListResponse sinkListResponse = null;
                for (SinkListResponse response : responses) {
                    if (response.getSinkName().equals(sinkRequest.getSinkName())) {
                        sinkListResponse = response;
                        break;
                    }
                }
                if (sinkListResponse == null) {
                    String sinkIndex = managerClient.createSink(sinkRequest);
                    return Double.valueOf(sinkIndex).intValue();
                }
                sinkRequest.setId(sinkListResponse.getId());
                Pair<Boolean, String> updateMsg = managerClient.updateSink(sinkRequest);
                if (updateMsg.getKey()) {
                    return sinkListResponse.getId();
                } else {
                    throw new RuntimeException(
                            String.format("Update sink:%s failed with ex:%s", GsonUtil.toJson(sinkRequest),
                                    updateMsg.getValue()));
                }
            }
        } else {
            throw new IllegalArgumentException(String.format("Unsupported sink type:%s", sinkType));
        }
    }
}
