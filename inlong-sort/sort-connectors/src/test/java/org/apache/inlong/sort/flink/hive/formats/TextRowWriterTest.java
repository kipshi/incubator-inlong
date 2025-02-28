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

package org.apache.inlong.sort.flink.hive.formats;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import io.airlift.compress.lzo.LzopCodec;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.flink.core.fs.local.LocalDataOutputStream;
import org.apache.flink.types.Row;
import org.apache.hadoop.io.compress.CompressionInputStream;
import org.apache.inlong.sort.configuration.Configuration;
import org.apache.inlong.sort.configuration.Constants.CompressionType;
import org.apache.inlong.sort.protocol.sink.HiveSinkInfo.TextFileFormat;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class TextRowWriterTest {
    @ClassRule
    public static TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void testWrite() throws IOException {
        File file = temporaryFolder.newFile("test.txt");
        TextRowWriter textRowWriter = new TextRowWriter(
                new LocalDataOutputStream(file),
                new TextFileFormat(','),
                new Configuration()
        );

        textRowWriter.addElement(Row.of("zhangsan", 1));
        textRowWriter.addElement(Row.of("lisi", 2));
        textRowWriter.flush();
        textRowWriter.finish();

        final List<String> results = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                new FileInputStream(file.getAbsolutePath()), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                results.add(line);
            }
        }

        assertEquals(2, results.size());
        assertEquals("zhangsan,1", results.get(0));
        assertEquals("lisi,2", results.get(1));
    }

    @Test
    public void testWriteGzip() throws IOException {
        File gzipFile = temporaryFolder.newFile("test.gz");
        TextRowWriter textRowWriter = new TextRowWriter(
                new LocalDataOutputStream(gzipFile),
                new TextFileFormat(',', CompressionType.GZIP),
                new Configuration()
        );

        textRowWriter.addElement(Row.of("zhangsan", 1));
        textRowWriter.addElement(Row.of("lisi", 2));

        textRowWriter.flush();
        textRowWriter.finish();

        assertTrue(isSameFile(gzipFile.getAbsolutePath(), "src/test/resources/testGzip.gz"));
    }

    @Test
    public void testWriteLZO() throws IOException {
        File lzoFile = temporaryFolder.newFile("test.lzo");
        TextRowWriter textRowWriter = new TextRowWriter(
                new LocalDataOutputStream(lzoFile),
                new TextFileFormat(',', CompressionType.LZO),
                new Configuration()
        );

        textRowWriter.addElement(Row.of("zhangsan", 1));
        textRowWriter.addElement(Row.of("lisi", 2));
        textRowWriter.finish();

        LzopCodec lzopCodec = new LzopCodec();
        CompressionInputStream compressionInputStream = lzopCodec.createInputStream(
                new FileInputStream(lzoFile.getAbsolutePath()));
        final List<String> results = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                compressionInputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                results.add(line);
            }
        }

        assertEquals(2, results.size());
        assertEquals("zhangsan,1", results.get(0));
        assertEquals("lisi,2", results.get(1));
    }

    public static boolean isSameFile(String fileName1, String fileName2) {
        try (FileInputStream fis1 = new FileInputStream(fileName1);
                FileInputStream fis2 = new FileInputStream(fileName2)) {
            int len1 = fis1.available();
            int len2 = fis2.available();

            if (len1 == len2) {
                byte[] data1 = new byte[len1];
                byte[] data2 = new byte[len2];

                fis1.read(data1);
                fis2.read(data2);

                return Arrays.equals(data1, data2);
            } else {
                return false;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
}
