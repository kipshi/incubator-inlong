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

package org.apache.inlong.sort.protocol.sink;

import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonSubTypes;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.apache.inlong.sort.configuration.Constants;
import org.apache.inlong.sort.protocol.FieldInfo;

import java.io.Serializable;
import java.util.Arrays;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * The base class of the data sink in the metadata.
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type")
@JsonSubTypes({
        @Type(value = ClickHouseSinkInfo.class, name = Constants.SINK_TYPE_CLICKHOUSE),
        @Type(value = HiveSinkInfo.class, name = Constants.SINK_TYPE_HIVE),
        @Type(value = KafkaSinkInfo.class, name = Constants.SINK_TYPE_KAFKA),
        @Type(value = IcebergSinkInfo.class, name = Constants.SINK_TYPE_ICEBERG),
        @Type(value = HbaseSinkInfo.class, name = Constants.SINK_TYPE_HBASE),
        @Type(value = ElasticsearchSinkInfo.class, name = Constants.SINK_TYPE_ES)}
)
public abstract class SinkInfo implements Serializable {

    private static final long serialVersionUID = 1485856855405721745L;

    @JsonProperty("fields")
    private final FieldInfo[] fields;

    public SinkInfo(@JsonProperty("fields") FieldInfo[] fields) {
        this.fields = checkNotNull(fields);
    }

    @JsonProperty("fields")
    public FieldInfo[] getFields() {
        return fields;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SinkInfo sinkInfo = (SinkInfo) o;
        return Arrays.equals(fields, sinkInfo.fields);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(fields);
    }
}
