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

package org.apache.inlong.manager.service.core.impl;

import com.google.common.collect.Lists;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.inlong.common.constant.Constants;
import org.apache.inlong.common.db.CommandEntity;
import org.apache.inlong.common.enums.PullJobTypeEnum;
import org.apache.inlong.common.enums.TaskTypeEnum;
import org.apache.inlong.common.pojo.agent.CmdConfig;
import org.apache.inlong.common.pojo.agent.DataConfig;
import org.apache.inlong.common.pojo.agent.TaskRequest;
import org.apache.inlong.common.pojo.agent.TaskResult;
import org.apache.inlong.common.pojo.agent.TaskSnapshotRequest;
import org.apache.inlong.manager.common.consts.SourceType;
import org.apache.inlong.manager.common.enums.SourceStatus;
import org.apache.inlong.manager.common.exceptions.BusinessException;
import org.apache.inlong.manager.dao.entity.InlongStreamEntity;
import org.apache.inlong.manager.dao.entity.StreamSourceEntity;
import org.apache.inlong.manager.dao.mapper.DataSourceCmdConfigEntityMapper;
import org.apache.inlong.manager.dao.mapper.InlongStreamEntityMapper;
import org.apache.inlong.manager.dao.mapper.StreamSourceEntityMapper;
import org.apache.inlong.manager.pojo.source.file.FileSourceDTO;
import org.apache.inlong.manager.service.core.AgentService;
import org.apache.inlong.manager.service.source.SourceSnapshotOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Agent service layer implementation
 */
@Service
public class AgentServiceImpl implements AgentService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentServiceImpl.class);
    private static final int UNISSUED_STATUS = 2;
    private static final int ISSUED_STATUS = 3;
    private static final int MODULUS_100 = 100;
    private static final int TASK_FETCH_SIZE = 2;

    @Autowired
    private StreamSourceEntityMapper sourceMapper;
    @Autowired
    private SourceSnapshotOperator snapshotOperator;
    @Autowired
    private DataSourceCmdConfigEntityMapper sourceCmdConfigMapper;
    @Autowired
    private InlongStreamEntityMapper streamMapper;

    @Override
    public Boolean reportSnapshot(TaskSnapshotRequest request) {
        return snapshotOperator.snapshot(request);
    }

    @Override
    @Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED,
            propagation = Propagation.REQUIRES_NEW)
    public void report(TaskRequest request) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("begin to get agent task: {}", request);
        }
        if (request == null || StringUtils.isBlank(request.getAgentIp())) {
            throw new BusinessException("agent request or agent ip was empty, just return");
        }

        // Update task status, other tasks with status 20x will change to 30x in next request
        if (CollectionUtils.isEmpty(request.getCommandInfo())) {
            LOGGER.warn("task result was empty, just return");
            return;
        }
        for (CommandEntity command : request.getCommandInfo()) {
            updateTaskStatus(command);
        }
    }

    /**
     * Update task status by command.
     *
     * @param command command info.
     */
    private void updateTaskStatus(CommandEntity command) {
        Integer taskId = command.getTaskId();
        StreamSourceEntity current = sourceMapper.selectForAgentTask(taskId);
        if (current == null) {
            LOGGER.warn("stream source not found by id={}, just return", taskId);
            return;
        }

        if (!Objects.equals(command.getVersion(), current.getVersion())) {
            LOGGER.warn("task result version [{}] not equals to current [{}] for id [{}], skip update",
                    command.getVersion(), current.getVersion(), taskId);
            return;
        }

        int result = command.getCommandResult();
        int previousStatus = current.getStatus();
        int nextStatus = SourceStatus.SOURCE_NORMAL.getCode();

        if (Constants.RESULT_FAIL == result) {
            LOGGER.warn("task failed for id =[{}]", taskId);
            nextStatus = SourceStatus.SOURCE_FAILED.getCode();
        } else if (previousStatus / MODULUS_100 == ISSUED_STATUS) {
            // Change the status from 30x to normal / disable / frozen
            if (SourceStatus.TEMP_TO_NORMAL.contains(previousStatus)) {
                nextStatus = SourceStatus.SOURCE_NORMAL.getCode();
            } else if (SourceStatus.BEEN_ISSUED_DELETE.getCode() == previousStatus) {
                nextStatus = SourceStatus.SOURCE_DISABLE.getCode();
            } else if (SourceStatus.BEEN_ISSUED_FROZEN.getCode() == previousStatus) {
                nextStatus = SourceStatus.SOURCE_FROZEN.getCode();
            }
        }

        if (nextStatus != previousStatus) {
            sourceMapper.updateStatus(taskId, nextStatus, false);
            LOGGER.info("task result=[{}], update source status to [{}] for id [{}]", result, nextStatus, taskId);
        }
    }

    @Override
    @Transactional(rollbackFor = Throwable.class, isolation = Isolation.READ_COMMITTED,
            propagation = Propagation.REQUIRES_NEW)
    public TaskResult getTaskResult(TaskRequest request) {
        if (request == null || StringUtils.isBlank(request.getAgentIp())) {
            throw new BusinessException("agent request or agent ip was empty, just return");
        }
        final String agentIp = request.getAgentIp();
        final String uuid = request.getUuid();

        List<StreamSourceEntity> tasks = Lists.newArrayList();
        // Query the tasks that needed to add or active - without agentIp and uuid
        List<StreamSourceEntity> nonFileTasks = fetchNonFileTasks(request);
        tasks.addAll(nonFileTasks);

        List<StreamSourceEntity> fileTasks = fetchFileTasks(request);
        tasks.addAll(fileTasks);

        // Query other tasks by agentIp and uuid - not included status with TO_BE_ISSUED_ADD and TO_BE_ISSUED_ACTIVE
        List<StreamSourceEntity> needIssuedTasks = fetchIssuedTasks(request);
        tasks.addAll(needIssuedTasks);

        // Query pending special commands
        List<CmdConfig> cmdConfigs = getAgentCmdConfigs(request);

        // Update agentIp and uuid for the added and active tasks
        for (StreamSourceEntity entity : tasks) {
            if (StringUtils.isEmpty(entity.getAgentIp())) {
                sourceMapper.updateIpAndUuid(entity.getId(), agentIp, uuid, false);
                LOGGER.info("update stream source ip to [{}], uuid to [{}] for id [{}]", agentIp, uuid, entity.getId());
            }
        }

        return TaskResult.builder().dataConfigs(dataConfigs).cmdConfigs(cmdConfigs).build();
    }

    private List<StreamSourceEntity> fetchNonFileTasks(TaskRequest taskRequest) {
        List<Integer> needAddStatusList;
        if (PullJobTypeEnum.NEVER == PullJobTypeEnum.getPullJobType(taskRequest.getPullJobType())) {
            LOGGER.warn("agent pull job type is [NEVER], just pull to be active tasks");
            needAddStatusList = Collections.singletonList(SourceStatus.TO_BE_ISSUED_ACTIVE.getCode());
        } else {
            needAddStatusList = Arrays.asList(SourceStatus.TO_BE_ISSUED_ADD.getCode(),
                    SourceStatus.TO_BE_ISSUED_ACTIVE.getCode());
        }
        List<String> sourceTypes = Lists.newArrayList(SourceType.MYSQL_BINLOG, SourceType.KAFKA,
                SourceType.MYSQL_SQL);
        return sourceMapper.selectByStatusAndType(needAddStatusList, sourceTypes, TASK_FETCH_SIZE);
    }

    private List<StreamSourceEntity> fetchFileTasks(TaskRequest taskRequest) {
        List<Integer> needAddStatusList;
        if (PullJobTypeEnum.NEVER == PullJobTypeEnum.getPullJobType(taskRequest.getPullJobType())) {
            LOGGER.warn("agent pull job type is [NEVER], just pull to be active tasks");
            needAddStatusList = Collections.singletonList(SourceStatus.TO_BE_ISSUED_ACTIVE.getCode());
        } else {
            needAddStatusList = Arrays.asList(SourceStatus.TO_BE_ISSUED_ADD.getCode(),
                    SourceStatus.TO_BE_ISSUED_ACTIVE.getCode());
        }
        final String agentIp = taskRequest.getAgentIp();
        final String agentClusterName = taskRequest.getClusterTag();
        List<StreamSourceEntity> fileEntities = sourceMapper.selectByStatusAndType(needAddStatusList,
                Lists.newArrayList(SourceType.FILE), TASK_FETCH_SIZE * 2);
        List<StreamSourceEntity> attachedFileEntities = Lists.newArrayList();
        for (StreamSourceEntity fileEntity : fileEntities) {
            FileSourceDTO fileSourceDTO = FileSourceDTO.getFromJson(fileEntity.getExtParams());
            final String ip = fileSourceDTO.getIp();
            final String clusterTag = fileSourceDTO.getClusterTag();
            if (StringUtils.isNotBlank(agentIp) && agentIp.equals(ip)) {
                attachedFileEntities.add(fileEntity);
                continue;
            }
            //todo problems
            if (StringUtils.isNotBlank(agentClusterName) && agentClusterName.equals(clusterTag)) {
                attachedFileEntities.add(fileEntity);
            }
        }
        return attachedFileEntities;
    }

    private List<StreamSourceEntity> fetchIssuedTasks(TaskRequest taskRequest) {
        final String agentIp = taskRequest.getAgentIp();
        final String uuid = taskRequest.getUuid();
        List<Integer> statusList = Arrays.asList(SourceStatus.TO_BE_ISSUED_DELETE.getCode(),
                SourceStatus.TO_BE_ISSUED_RETRY.getCode(), SourceStatus.TO_BE_ISSUED_BACKTRACK.getCode(),
                SourceStatus.TO_BE_ISSUED_FROZEN.getCode(), SourceStatus.TO_BE_ISSUED_CHECK.getCode(),
                SourceStatus.TO_BE_ISSUED_REDO_METRIC.getCode(), SourceStatus.TO_BE_ISSUED_MAKEUP.getCode());
        return sourceMapper.selectByStatusAndIp(statusList, agentIp, uuid);
    }

    /**
     * Get the DataConfig from the stream source entity.
     *
     * @param entity stream source entity.
     * @param op operation code for add, delete, etc.
     * @return data config.
     */
    private DataConfig getDataConfig(StreamSourceEntity entity, int op) {
        DataConfig dataConfig = new DataConfig();
        dataConfig.setIp(entity.getAgentIp());
        dataConfig.setUuid(entity.getUuid());
        dataConfig.setOp(String.valueOf(op));
        dataConfig.setTaskId(entity.getId());
        dataConfig.setTaskType(getTaskType(entity));
        dataConfig.setTaskName(entity.getSourceName());
        dataConfig.setSnapshot(entity.getSnapshot());
        dataConfig.setExtParams(entity.getExtParams());
        dataConfig.setVersion(entity.getVersion());

        String groupId = entity.getInlongGroupId();
        String streamId = entity.getInlongStreamId();
        dataConfig.setInlongGroupId(groupId);
        dataConfig.setInlongStreamId(streamId);
        InlongStreamEntity streamEntity = streamMapper.selectByIdentifier(groupId, streamId);
        if (streamEntity != null) {
            dataConfig.setSyncSend(streamEntity.getSyncSend());
        } else {
            dataConfig.setSyncSend(0);
            LOGGER.warn("set syncSend=[0] as the stream not exists for groupId={}, streamId={}", groupId, streamId);
        }
        return dataConfig;
    }

    /**
     * Get the Task type from the stream source entity.
     *
     * @param sourceEntity stream source info.
     * @return task type
     */
    private int getTaskType(StreamSourceEntity sourceEntity) {
        TaskTypeEnum taskType = SourceType.SOURCE_TASK_MAP.get(sourceEntity.getSourceType());
        if (taskType == null) {
            throw new BusinessException("Unsupported task type for source type " + sourceEntity.getSourceType());
        }
        return taskType.getType();
    }

    /**
     * Get the agent command config by the agent ip.
     *
     * @param taskRequest task request info.
     * @return agent command config list.
     */
    private List<CmdConfig> getAgentCmdConfigs(TaskRequest taskRequest) {
        return sourceCmdConfigMapper.queryCmdByAgentIp(taskRequest.getAgentIp()).stream().map(cmd -> {
            CmdConfig cmdConfig = new CmdConfig();
            cmdConfig.setDataTime(cmd.getSpecifiedDataTime());
            cmdConfig.setOp(cmd.getCmdType());
            cmdConfig.setId(cmd.getId());
            cmdConfig.setTaskId(cmd.getTaskId());
            return cmdConfig;
        }).collect(Collectors.toList());
    }

}