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

package org.apache.inlong.manager.client.api.util;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.compress.utils.Lists;
import org.apache.inlong.manager.client.api.DataFormat;
import org.apache.inlong.manager.client.api.DataSeparator;
import org.apache.inlong.manager.client.api.SinkField;
import org.apache.inlong.manager.client.api.StreamSink;
import org.apache.inlong.manager.client.api.auth.DefaultAuthentication;
import org.apache.inlong.manager.client.api.sink.ClickHouseSink;
import org.apache.inlong.manager.client.api.sink.HiveSink;
import org.apache.inlong.manager.client.api.sink.KafkaSink;
import org.apache.inlong.manager.common.enums.FieldType;
import org.apache.inlong.manager.common.enums.FileFormat;
import org.apache.inlong.manager.common.enums.SinkType;
import org.apache.inlong.manager.common.pojo.sink.SinkFieldRequest;
import org.apache.inlong.manager.common.pojo.sink.SinkFieldResponse;
import org.apache.inlong.manager.common.pojo.sink.SinkRequest;
import org.apache.inlong.manager.common.pojo.sink.SinkResponse;
import org.apache.inlong.manager.common.pojo.sink.ck.ClickHouseSinkRequest;
import org.apache.inlong.manager.common.pojo.sink.ck.ClickHouseSinkResponse;
import org.apache.inlong.manager.common.pojo.sink.hive.HiveSinkRequest;
import org.apache.inlong.manager.common.pojo.sink.hive.HiveSinkResponse;
import org.apache.inlong.manager.common.pojo.sink.kafka.KafkaSinkRequest;
import org.apache.inlong.manager.common.pojo.sink.kafka.KafkaSinkResponse;
import org.apache.inlong.manager.common.pojo.stream.InlongStreamResponse;
import org.apache.inlong.manager.common.util.CommonBeanUtils;

import java.nio.charset.Charset;
import java.util.List;
import java.util.stream.Collectors;

public class InlongStreamSinkTransfer {

    public static SinkRequest createSinkRequest(StreamSink streamSink, InlongStreamResponse streamInfo) {
        SinkType sinkType = streamSink.getSinkType();
        SinkRequest sinkRequest;
        if (sinkType == SinkType.HIVE) {
            sinkRequest = createHiveRequest(streamSink, streamInfo);
        } else if (sinkType == SinkType.KAFKA) {
            sinkRequest = createKafkaRequest(streamSink, streamInfo);
        } else if (sinkType == SinkType.CLICKHOUSE) {
            sinkRequest = createClickHouseRequest(streamSink, streamInfo);
        } else {
            throw new IllegalArgumentException(String.format("Unsupport sink type : %s for Inlong", sinkType));
        }
        return sinkRequest;
    }

    public static StreamSink parseStreamSink(SinkResponse sinkResponse) {
        return parseStreamSink(sinkResponse, null);
    }

    public static StreamSink parseStreamSink(SinkResponse sinkResponse, StreamSink streamSink) {
        String type = sinkResponse.getSinkType();
        SinkType sinkType = SinkType.forType(type);
        StreamSink streamSinkResult;
        if (sinkType == SinkType.HIVE) {
            streamSinkResult = parseHiveSink((HiveSinkResponse) sinkResponse, streamSink);
        } else if (sinkType == SinkType.KAFKA) {
            streamSinkResult = parseKafkaSink((KafkaSinkResponse) sinkResponse, streamSink);
        } else if (sinkType == SinkType.CLICKHOUSE) {
            streamSinkResult = parseClickHouseSink((ClickHouseSinkResponse) sinkResponse, streamSink);
        } else {
            throw new IllegalArgumentException(String.format("Unsupport sink type : %s for Inlong", sinkType));
        }
        return streamSinkResult;
    }

    private static SinkRequest createClickHouseRequest(StreamSink streamSink, InlongStreamResponse streamInfo) {
        ClickHouseSinkRequest clickHouseSinkRequest = new ClickHouseSinkRequest();
        ClickHouseSink clickHouseSink = (ClickHouseSink) streamSink;
        clickHouseSinkRequest.setSinkName(clickHouseSink.getSinkName());
        clickHouseSinkRequest.setDatabaseName(clickHouseSink.getDatabaseName());
        clickHouseSinkRequest.setSinkType(clickHouseSink.getSinkType().name());
        clickHouseSinkRequest.setJdbcUrl(clickHouseSink.getJdbcUrl());
        DefaultAuthentication defaultAuthentication = clickHouseSink.getAuthentication();
        AssertUtil.notNull(defaultAuthentication,
                String.format("Clickhouse storage:%s must be authenticated", clickHouseSink.getDatabaseName()));
        clickHouseSinkRequest.setUsername(defaultAuthentication.getUserName());
        clickHouseSinkRequest.setPassword(defaultAuthentication.getPassword());
        clickHouseSinkRequest.setTableName(clickHouseSink.getTableName());
        clickHouseSinkRequest.setDistributedTable(clickHouseSink.getDistributedTable());
        clickHouseSinkRequest.setFlushInterval(clickHouseSink.getFlushInterval());
        clickHouseSinkRequest.setFlushRecordNumber(clickHouseSink.getFlushRecordNumber());
        clickHouseSinkRequest.setKeyFieldNames(clickHouseSink.getKeyFieldNames());
        clickHouseSinkRequest.setPartitionKey(clickHouseSink.getPartitionKey());
        clickHouseSinkRequest.setPartitionStrategy(clickHouseSink.getPartitionStrategy());
        clickHouseSinkRequest.setWriteMaxRetryTimes(clickHouseSink.getWriteMaxRetryTimes());
        clickHouseSinkRequest.setInlongGroupId(streamInfo.getInlongGroupId());
        clickHouseSinkRequest.setInlongStreamId(streamInfo.getInlongStreamId());
        clickHouseSinkRequest.setProperties(clickHouseSink.getProperties());
        clickHouseSinkRequest.setEnableCreateResource(clickHouseSink.isNeedCreated() ? 1 : 0);
        if (CollectionUtils.isNotEmpty(clickHouseSink.getSinkFields())) {
            List<SinkFieldRequest> fieldRequests = createSinkFieldRequests(streamSink.getSinkFields());
            clickHouseSinkRequest.setFieldList(fieldRequests);
        }
        return clickHouseSinkRequest;
    }

    private static StreamSink parseClickHouseSink(ClickHouseSinkResponse sinkResponse,
            StreamSink streamSink) {
        ClickHouseSink clickHouseSink = new ClickHouseSink();
        if (streamSink != null) {
            AssertUtil.isTrue(sinkResponse.getSinkName().equals(streamSink.getSinkName()),
                    String.format("SinkName is not equal: %s != %s", sinkResponse, streamSink));
            ClickHouseSink snapshot = (ClickHouseSink) streamSink;
            clickHouseSink = CommonBeanUtils.copyProperties(snapshot, ClickHouseSink::new);
        } else {
            clickHouseSink.setDistributedTable(sinkResponse.getDistributedTable());
            clickHouseSink.setSinkName(sinkResponse.getSinkName());
            clickHouseSink.setFlushInterval(sinkResponse.getFlushInterval());
            clickHouseSink.setAuthentication(new DefaultAuthentication(sinkResponse.getSinkName(),
                    sinkResponse.getPassword()));
            clickHouseSink.setDatabaseName(sinkResponse.getDatabaseName());
            clickHouseSink.setFlushRecordNumber(sinkResponse.getFlushRecordNumber());
            clickHouseSink.setJdbcUrl(sinkResponse.getJdbcUrl());
            clickHouseSink.setPartitionKey(sinkResponse.getPartitionKey());
            clickHouseSink.setKeyFieldNames(sinkResponse.getKeyFieldNames());
            clickHouseSink.setPartitionStrategy(sinkResponse.getPartitionStrategy());
            clickHouseSink.setWriteMaxRetryTimes(sinkResponse.getWriteMaxRetryTimes());
            clickHouseSink.setDistributedTable(sinkResponse.getDistributedTable());
        }
        clickHouseSink.setProperties(sinkResponse.getProperties());
        clickHouseSink.setNeedCreated(sinkResponse.getEnableCreateResource() == 1);
        if (CollectionUtils.isNotEmpty(sinkResponse.getFieldList())) {
            clickHouseSink.setSinkFields(convertToSinkFields(sinkResponse.getFieldList()));
        }
        return clickHouseSink;
    }

    private static List<SinkField> convertToSinkFields(List<SinkFieldResponse> sinkFieldResponses) {
        return sinkFieldResponses.stream().map(sinkFieldResponse -> new SinkField(sinkFieldResponse.getId(),
                FieldType.forName(sinkFieldResponse.getFieldType()),
                sinkFieldResponse.getFieldName(),
                sinkFieldResponse.getFieldComment(),
                null, sinkFieldResponse.getSourceFieldName(),
                sinkFieldResponse.getSourceFieldType(),
                sinkFieldResponse.getIsMetaField())).collect(Collectors.toList());

    }

    private static SinkRequest createKafkaRequest(StreamSink streamSink, InlongStreamResponse streamInfo) {
        KafkaSinkRequest kafkaSinkRequest = new KafkaSinkRequest();
        KafkaSink kafkaSink = (KafkaSink) streamSink;
        kafkaSinkRequest.setSinkName(streamSink.getSinkName());
        kafkaSinkRequest.setAddress(kafkaSink.getAddress());
        kafkaSinkRequest.setTopicName(kafkaSink.getTopicName());
        kafkaSinkRequest.setSinkType(kafkaSink.getSinkType().name());
        kafkaSinkRequest.setInlongGroupId(streamInfo.getInlongGroupId());
        kafkaSinkRequest.setInlongStreamId(streamInfo.getInlongStreamId());
        kafkaSinkRequest.setSerializationType(kafkaSink.getDataFormat().name());
        kafkaSinkRequest.setEnableCreateResource(kafkaSink.isNeedCreated() ? 1 : 0);
        kafkaSinkRequest.setProperties(kafkaSink.getProperties());
        if (CollectionUtils.isNotEmpty(kafkaSink.getSinkFields())) {
            List<SinkFieldRequest> fieldRequests = createSinkFieldRequests(kafkaSink.getSinkFields());
            kafkaSinkRequest.setFieldList(fieldRequests);
        }
        return kafkaSinkRequest;
    }

    private static StreamSink parseKafkaSink(KafkaSinkResponse sinkResponse, StreamSink sink) {
        KafkaSink kafkaSink = new KafkaSink();
        if (sink != null) {
            AssertUtil.isTrue(sinkResponse.getSinkName().equals(sink.getSinkName()),
                    String.format("SinkName is not equal: %s != %s", sinkResponse, sink));
            KafkaSink snapshot = (KafkaSink) sink;
            kafkaSink.setSinkName(snapshot.getSinkName());
            kafkaSink.setAddress(snapshot.getAddress());
            kafkaSink.setTopicName(snapshot.getTopicName());
            kafkaSink.setDataFormat(snapshot.getDataFormat());
        } else {
            kafkaSink.setSinkName(sinkResponse.getSinkName());
            kafkaSink.setAddress(sinkResponse.getAddress());
            kafkaSink.setTopicName(sinkResponse.getTopicName());
            kafkaSink.setDataFormat(DataFormat.forName(sinkResponse.getSerializationType()));
        }
        kafkaSink.setProperties(sinkResponse.getProperties());
        kafkaSink.setNeedCreated(sinkResponse.getEnableCreateResource() == 1);
        if (CollectionUtils.isNotEmpty(sinkResponse.getFieldList())) {
            kafkaSink.setSinkFields(convertToSinkFields(sinkResponse.getFieldList()));
        }
        return kafkaSink;
    }

    private static HiveSinkRequest createHiveRequest(StreamSink streamSink, InlongStreamResponse streamInfo) {
        HiveSinkRequest hiveSinkRequest = new HiveSinkRequest();
        HiveSink hiveSink = (HiveSink) streamSink;
        hiveSinkRequest.setSinkName(streamSink.getSinkName());
        hiveSinkRequest.setInlongGroupId(streamInfo.getInlongGroupId());
        hiveSinkRequest.setInlongStreamId(streamInfo.getInlongStreamId());
        hiveSinkRequest.setDataEncoding(hiveSink.getCharset().name());
        hiveSinkRequest.setEnableCreateTable(hiveSink.isNeedCreated() ? 1 : 0);
        hiveSinkRequest.setDataSeparator(String.valueOf(hiveSink.getDataSeparator().getAsciiCode()));
        hiveSinkRequest.setDbName(hiveSink.getDbName());
        hiveSinkRequest.setTableName(hiveSink.getTableName());
        hiveSinkRequest.setDataPath(hiveSink.getDataPath());
        hiveSinkRequest.setJdbcUrl(hiveSink.getJdbcUrl());
        hiveSinkRequest.setFileFormat(hiveSink.getFileFormat().name());
        hiveSinkRequest.setSinkType(hiveSink.getSinkType().name());
        DefaultAuthentication defaultAuthentication = hiveSink.getAuthentication();
        AssertUtil.notNull(defaultAuthentication,
                String.format("Hive storage:%s must be authenticated", hiveSink.getDbName()));
        hiveSinkRequest.setUsername(defaultAuthentication.getUserName());
        hiveSinkRequest.setPassword(defaultAuthentication.getPassword());
        hiveSinkRequest.setPrimaryPartition(hiveSink.getPrimaryPartition());
        hiveSinkRequest.setSecondaryPartition(hiveSink.getSecondaryPartition());
        hiveSinkRequest.setProperties(hiveSink.getProperties());
        if (CollectionUtils.isNotEmpty(hiveSink.getSinkFields())) {
            List<SinkFieldRequest> fieldRequests = createSinkFieldRequests(streamSink.getSinkFields());
            hiveSinkRequest.setFieldList(fieldRequests);
        }
        return hiveSinkRequest;
    }

    private static List<SinkFieldRequest> createSinkFieldRequests(List<SinkField> sinkFields) {
        List<SinkFieldRequest> fieldRequestList = Lists.newArrayList();
        for (SinkField sinkField : sinkFields) {
            SinkFieldRequest request = new SinkFieldRequest();
            request.setFieldName(sinkField.getFieldName());
            request.setFieldType(sinkField.getFieldType().toString());
            request.setFieldComment(sinkField.getFieldComment());
            request.setSourceFieldName(sinkField.getSourceFieldName());
            request.setSourceFieldType(sinkField.getSourceFieldType());
            request.setIsMetaField(sinkField.getIsMetaField());
            fieldRequestList.add(request);
        }
        return fieldRequestList;
    }

    private static HiveSink parseHiveSink(HiveSinkResponse sinkResponse, StreamSink sink) {
        HiveSink hiveSink = new HiveSink();
        if (sink != null) {
            AssertUtil.isTrue(sinkResponse.getSinkName().equals(sink.getSinkName()),
                    String.format("SinkName is not equal: %s != %s", sinkResponse, sink));
            HiveSink snapshot = (HiveSink) sink;
            hiveSink.setSinkName(snapshot.getSinkName());
            hiveSink.setDataSeparator(snapshot.getDataSeparator());
            hiveSink.setCharset(snapshot.getCharset());
            hiveSink.setAuthentication(snapshot.getAuthentication());
            hiveSink.setFileFormat(snapshot.getFileFormat());
            hiveSink.setJdbcUrl(snapshot.getJdbcUrl());
            hiveSink.setTableName(snapshot.getTableName());
            hiveSink.setDbName(snapshot.getDbName());
            hiveSink.setDataPath(snapshot.getDataPath());
            hiveSink.setSecondaryPartition(snapshot.getSecondaryPartition());
            hiveSink.setPrimaryPartition(snapshot.getPrimaryPartition());
        } else {
            hiveSink.setSinkName(sinkResponse.getSinkName());
            hiveSink.setDataSeparator(DataSeparator.forAscii(Integer.parseInt(sinkResponse.getDataSeparator())));
            hiveSink.setCharset(Charset.forName(sinkResponse.getDataEncoding()));
            String password = sinkResponse.getPassword();
            String uname = sinkResponse.getUsername();
            hiveSink.setAuthentication(new DefaultAuthentication(uname, password));
            hiveSink.setFileFormat(FileFormat.forName(sinkResponse.getFileFormat()));
            hiveSink.setJdbcUrl(sinkResponse.getJdbcUrl());
            hiveSink.setTableName(sinkResponse.getTableName());
            hiveSink.setDbName(sinkResponse.getDbName());
            hiveSink.setDataPath(sinkResponse.getDataPath());
            hiveSink.setSecondaryPartition(sinkResponse.getSecondaryPartition());
            hiveSink.setPrimaryPartition(sinkResponse.getPrimaryPartition());
        }
        hiveSink.setProperties(sinkResponse.getProperties());
        hiveSink.setSinkType(SinkType.HIVE);
        hiveSink.setNeedCreated(sinkResponse.getEnableCreateResource() == 1);
        if (CollectionUtils.isNotEmpty(sinkResponse.getFieldList())) {
            hiveSink.setSinkFields(convertToSinkFields(sinkResponse.getFieldList()));
        }
        return hiveSink;
    }

}
