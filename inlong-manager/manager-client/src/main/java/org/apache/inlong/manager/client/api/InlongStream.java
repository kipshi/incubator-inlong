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

package org.apache.inlong.manager.client.api;

import org.apache.inlong.manager.common.pojo.stream.StreamField;
import org.apache.inlong.manager.common.pojo.stream.StreamPipeline;
import org.apache.inlong.manager.common.pojo.stream.StreamSink;
import org.apache.inlong.manager.common.pojo.stream.StreamSource;
import org.apache.inlong.manager.common.pojo.stream.StreamTransform;

import java.util.List;
import java.util.Map;

public abstract class InlongStream {

    public abstract String getName();

    public abstract List<StreamField> listFields();

    public abstract Map<String, StreamSource> getSources();

    public abstract Map<String, StreamSink> getSinks();

    public abstract Map<String, StreamTransform> getTransforms();

    public abstract void addSource(StreamSource source);

    public abstract void addSink(StreamSink sink);

    public abstract void addTransform(StreamTransform transform);

    public abstract StreamPipeline createPipeline();

    @Deprecated
    public abstract void updateSource(StreamSource source);

    @Deprecated
    public abstract void updateSink(StreamSink sink);
}
