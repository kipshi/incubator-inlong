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

package org.apache.inlong.manager.service.resource.hbase;

import org.apache.commons.collections.CollectionUtils;
import org.apache.inlong.manager.common.enums.GlobalConstants;
import org.apache.inlong.manager.common.enums.SinkStatus;
import org.apache.inlong.manager.common.enums.SinkType;
import org.apache.inlong.manager.common.exceptions.WorkflowException;
import org.apache.inlong.manager.common.pojo.sink.SinkInfo;
import org.apache.inlong.manager.common.pojo.sink.hbase.HbaseColumnFamilyInfo;
import org.apache.inlong.manager.common.pojo.sink.hbase.HbaseSinkDTO;
import org.apache.inlong.manager.common.pojo.sink.hbase.HbaseTableInfo;
import org.apache.inlong.manager.dao.entity.StreamSinkFieldEntity;
import org.apache.inlong.manager.dao.mapper.StreamSinkFieldEntityMapper;
import org.apache.inlong.manager.service.resource.SinkResourceOperator;
import org.apache.inlong.manager.service.sink.StreamSinkService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.stream.Collectors.toList;

/**
 * hbase resource operator
 */
@Service
public class HbaseResourceOperator implements SinkResourceOperator {

    private static final Logger LOGGER = LoggerFactory.getLogger(HbaseResourceOperator.class);

    @Autowired
    private StreamSinkService sinkService;
    @Autowired
    private StreamSinkFieldEntityMapper sinkFieldMapper;

    @Override
    public Boolean accept(SinkType sinkType) {
        return SinkType.HBASE == sinkType;
    }

    /**
     * Create hbase table according to the sink config
     */
    public void createSinkResource(SinkInfo sinkInfo) {
        if (sinkInfo == null) {
            LOGGER.warn("sink info was null, skip to create resource");
            return;
        }

        if (SinkStatus.CONFIG_SUCCESSFUL.getCode().equals(sinkInfo.getStatus())) {
            LOGGER.warn("sink resource [" + sinkInfo.getId() + "] already success, skip to create");
            return;
        } else if (GlobalConstants.DISABLE_CREATE_RESOURCE.equals(sinkInfo.getEnableCreateResource())) {
            LOGGER.warn("create resource was disabled, skip to create for [" + sinkInfo.getId() + "]");
            return;
        }

        this.createTable(sinkInfo);
    }

    private void createTable(SinkInfo sinkInfo) {
        LOGGER.info("begin to create hbase table for sinkInfo={}", sinkInfo);

        // Get all info from config
        HbaseSinkDTO hbaseInfo = HbaseSinkDTO.getFromJson(sinkInfo.getExtParams());
        List<HbaseColumnFamilyInfo> columnFamilies = getColumnFamilies(sinkInfo);
        if (CollectionUtils.isEmpty(columnFamilies)) {
            throw new IllegalArgumentException("no hbase column families specified");
        }
        HbaseTableInfo tableInfo = HbaseSinkDTO.getHbaseTableInfo(hbaseInfo, columnFamilies);

        String zkAddress = hbaseInfo.getZookeeperQuorum();
        String zkNode = hbaseInfo.getZookeeperZnodeParent();
        String namespace = hbaseInfo.getNamespace();
        String tableName = hbaseInfo.getTableName();

        try {
            // 1. create database if not exists
            HbaseApiUtils.createNamespace(zkAddress, zkNode, namespace);

            // 2. check if the table exists
            boolean tableExists = HbaseApiUtils.tableExists(zkAddress, zkNode, namespace, tableName);

            if (!tableExists) {
                // 3. create table
                HbaseApiUtils.createTable(zkAddress, zkNode, tableInfo);
            } else {
                // 4. or update table columns
                List<HbaseColumnFamilyInfo> existColumnFamilies = HbaseApiUtils.getColumnFamilies(zkAddress, zkNode,
                                namespace, tableName).stream()
                        .sorted(Comparator.comparing(HbaseColumnFamilyInfo::getCfName)).collect(toList());
                List<HbaseColumnFamilyInfo> requestColumnFamilies = tableInfo.getColumnFamilies().stream()
                        .sorted(Comparator.comparing(HbaseColumnFamilyInfo::getCfName)).collect(toList());
                List<HbaseColumnFamilyInfo> newColumnFamilies = requestColumnFamilies.stream()
                        .skip(existColumnFamilies.size()).collect(toList());

                if (CollectionUtils.isNotEmpty(newColumnFamilies)) {
                    HbaseApiUtils.addColumnFamilies(zkAddress, zkNode, namespace, tableName, newColumnFamilies);
                    LOGGER.info("{} column families added for table {}", newColumnFamilies.size(), tableName);
                }
            }
            String info = "success to create hbase resource";
            sinkService.updateStatus(sinkInfo.getId(), SinkStatus.CONFIG_SUCCESSFUL.getCode(), info);
            LOGGER.info(info + " for sinkInfo = {}", info);
        } catch (Throwable e) {
            String errMsg = "create hbase table failed: " + e.getMessage();
            LOGGER.error(errMsg, e);
            sinkService.updateStatus(sinkInfo.getId(), SinkStatus.CONFIG_FAILED.getCode(), errMsg);
            throw new WorkflowException(errMsg);
        }
    }

    private List<HbaseColumnFamilyInfo> getColumnFamilies(SinkInfo sinkInfo) {
        List<StreamSinkFieldEntity> fieldList = sinkFieldMapper.selectBySinkId(sinkInfo.getId());
        Set<String> seen = new HashSet<>();

        List<HbaseColumnFamilyInfo> columnFamilies = new ArrayList<>();
        for (StreamSinkFieldEntity field : fieldList) {
            HbaseColumnFamilyInfo columnFamily = HbaseColumnFamilyInfo.getFromJson(field.getExtrParam());
            if (seen.contains(columnFamily.getCfName())) {
                continue;
            }
            seen.add(columnFamily.getCfName());
            columnFamilies.add(columnFamily);
        }

        return columnFamilies;
    }
}
