/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.inlong.sort.singletenant.flink.utils;

import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.inlong.sort.formats.common.BooleanFormatInfo;
import org.apache.inlong.sort.formats.common.StringFormatInfo;
import org.apache.inlong.sort.protocol.FieldInfo;
import org.junit.Test;

import static org.apache.inlong.sort.singletenant.flink.utils.CommonUtils.convertFieldInfosToRowTypeInfo;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class CommonUtilsTest {

    @Test
    public void testConvertFieldInfosToRowTypeInfo() {
        org.apache.flink.api.java.typeutils.RowTypeInfo rowTypeInfoFlink =
                convertFieldInfosToRowTypeInfo(new FieldInfo[]{
                        new FieldInfo("field1", new StringFormatInfo()),
                        new FieldInfo("field2", new BooleanFormatInfo())
                });
        assertArrayEquals(new String[]{"field1", "field2"}, rowTypeInfoFlink.getFieldNames());
        TypeInformation<?>[] fieldTypesFlink = rowTypeInfoFlink.getFieldTypes();
        assertEquals(Types.STRING, fieldTypesFlink[0]);
        assertEquals(Types.BOOLEAN, fieldTypesFlink[1]);
    }
}
