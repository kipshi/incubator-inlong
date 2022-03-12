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

package org.apache.inlong.manager.service.sink;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.inlong.manager.common.enums.Constant;
import org.apache.inlong.manager.common.enums.EntityStatus;
import org.apache.inlong.manager.common.enums.ErrorCodeEnum;
import org.apache.inlong.manager.common.enums.GroupState;
import org.apache.inlong.manager.common.enums.SinkType;
import org.apache.inlong.manager.common.pojo.group.InlongGroupInfo;
import org.apache.inlong.manager.common.pojo.sink.SinkApproveDTO;
import org.apache.inlong.manager.common.pojo.sink.SinkBriefResponse;
import org.apache.inlong.manager.common.pojo.sink.SinkListResponse;
import org.apache.inlong.manager.common.pojo.sink.SinkPageRequest;
import org.apache.inlong.manager.common.pojo.sink.SinkRequest;
import org.apache.inlong.manager.common.pojo.sink.SinkResponse;
import org.apache.inlong.manager.common.pojo.workflow.form.GroupResourceProcessForm;
import org.apache.inlong.manager.common.util.CommonBeanUtils;
import org.apache.inlong.manager.common.util.Preconditions;
import org.apache.inlong.manager.dao.entity.InlongGroupEntity;
import org.apache.inlong.manager.dao.entity.StreamSinkEntity;
import org.apache.inlong.manager.dao.mapper.StreamSinkEntityMapper;
import org.apache.inlong.manager.dao.mapper.StreamSinkFieldEntityMapper;
import org.apache.inlong.manager.service.CommonOperateService;
import org.apache.inlong.manager.service.workflow.ProcessName;
import org.apache.inlong.manager.service.workflow.WorkflowService;
import org.apache.inlong.manager.service.workflow.stream.CreateStreamWorkflowDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy;
import java.util.concurrent.TimeUnit;

/**
 * Implementation of sink service interface
 */
@Service
public class StreamSinkServiceImpl implements StreamSinkService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StreamSinkServiceImpl.class);
    public final ExecutorService executorService = new ThreadPoolExecutor(
            10,
            20,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(100),
            new ThreadFactoryBuilder().setNameFormat("stream-workflow-%s").build(),
            new CallerRunsPolicy());

    @Autowired
    private SinkOperationFactory operationFactory;
    @Autowired
    private CommonOperateService commonOperateService;
    @Autowired
    private StreamSinkEntityMapper sinkMapper;
    @Autowired
    private StreamSinkFieldEntityMapper sinkFieldMapper;
    @Autowired
    private WorkflowService workflowService;

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public Integer save(SinkRequest request, String operator) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("begin to save sink info=" + request);
        }
        this.checkParams(request);

        // Check if it can be added
        String groupId = request.getInlongGroupId();
        InlongGroupEntity groupEntity = commonOperateService.checkGroupStatus(groupId, operator);

        // Make sure that there is no sink info with the current groupId and streamId
        String streamId = request.getInlongStreamId();
        String sinkType = request.getSinkType();
        List<StreamSinkEntity> sinkExist = sinkMapper.selectByIdAndType(groupId, streamId, sinkType);
        Preconditions.checkEmpty(sinkExist, ErrorCodeEnum.SINK_ALREADY_EXISTS.getMessage());

        // According to the sink type, save sink information
        StreamSinkOperation operation = operationFactory.getInstance(SinkType.forType(sinkType));
        int id = operation.saveOpt(request, operator);

        // If the inlong group status is [Configuration Successful], then asynchronously initiate
        // the [Single inlong stream Resource Creation] workflow
        if (GroupState.CONFIG_SUCCESSFUL.getCode().equals(groupEntity.getStatus())) {
            executorService.execute(new WorkflowStartRunnable(operator, groupEntity, streamId));
        }

        LOGGER.info("success to save sink info");
        return id;
    }

    @Override
    public SinkResponse get(Integer id, String sinkType) {
        LOGGER.debug("begin to get sink by id={}, sinkType={}", id, sinkType);
        StreamSinkOperation operation = operationFactory.getInstance(SinkType.forType(sinkType));
        SinkResponse sinkResponse = operation.getById(sinkType, id);
        LOGGER.debug("success to get sink info");
        return sinkResponse;
    }

    @Override
    public Integer getCount(String groupId, String streamId) {
        Integer count = sinkMapper.selectCount(groupId, streamId);
        LOGGER.debug("sink count={} with groupId={}, streamId={}", count, groupId, streamId);
        return count;
    }

    @Override
    public List<SinkResponse> listSink(String groupId, String streamId) {
        LOGGER.debug("begin to list sink by groupId={}, streamId={}", groupId, streamId);
        Preconditions.checkNotNull(groupId, Constant.GROUP_ID_IS_EMPTY);

        List<StreamSinkEntity> entityList = sinkMapper.selectByRelatedId(groupId, streamId);
        if (CollectionUtils.isEmpty(entityList)) {
            return Collections.emptyList();
        }
        List<SinkResponse> responseList = new ArrayList<>();
        entityList.forEach(entity -> responseList.add(this.get(entity.getId(), entity.getSinkType())));

        LOGGER.info("success to list sink");
        return responseList;
    }

    @Override
    public List<SinkBriefResponse> listBrief(String groupId, String streamId) {
        LOGGER.debug("begin to list sink summary by groupId=" + groupId + ", streamId=" + streamId);
        Preconditions.checkNotNull(groupId, Constant.GROUP_ID_IS_EMPTY);
        Preconditions.checkNotNull(streamId, Constant.STREAM_ID_IS_EMPTY);

        // Query all sink information and encapsulate it in the result set
        List<SinkBriefResponse> summaryList = sinkMapper.selectSummary(groupId, streamId);

        LOGGER.debug("success to list sink summary");
        return summaryList;
    }

    @Override
    public PageInfo<? extends SinkListResponse> listByCondition(SinkPageRequest request) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("begin to list sink page by " + request);
        }
        Preconditions.checkNotNull(request.getInlongGroupId(), Constant.GROUP_ID_IS_EMPTY);

        PageHelper.startPage(request.getPageNum(), request.getPageSize());
        List<StreamSinkEntity> entityPage = sinkMapper.selectByCondition(request);
        Map<SinkType, Page<StreamSinkEntity>> sinkMap = Maps.newHashMap();
        for (StreamSinkEntity streamSink : entityPage) {
            SinkType sinkType = SinkType.forType(streamSink.getSinkType());
            sinkMap.computeIfAbsent(sinkType, k -> new Page<>()).add(streamSink);
        }
        List<SinkListResponse> sinkListResponses = Lists.newArrayList();
        for (Map.Entry<SinkType, Page<StreamSinkEntity>> entry : sinkMap.entrySet()) {
            SinkType sinkType = entry.getKey();
            StreamSinkOperation operation = operationFactory.getInstance(sinkType);
            PageInfo<? extends SinkListResponse> pageInfo = operation.getPageInfo(entry.getValue());
            sinkListResponses.addAll(pageInfo.getList());
        }
        // Encapsulate the paging query results into the PageInfo object to obtain related paging information
        PageInfo<? extends SinkListResponse> pageInfo = PageInfo.of(sinkListResponses);

        LOGGER.debug("success to list sink page");
        return pageInfo;
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public Boolean update(SinkRequest request, String operator) {
        LOGGER.info("begin to update sink info={}", request);
        this.checkParams(request);
        Preconditions.checkNotNull(request.getId(), Constant.ID_IS_EMPTY);

        // Check if it can be modified
        String groupId = request.getInlongGroupId();
        InlongGroupEntity groupEntity = commonOperateService.checkGroupStatus(groupId, operator);

        String streamId = request.getInlongStreamId();
        String sinkType = request.getSinkType();

        StreamSinkOperation operation = operationFactory.getInstance(SinkType.forType(sinkType));
        operation.updateOpt(request, operator);

        // The inlong group status is [Configuration successful], then asynchronously initiate
        // the [Single inlong stream resource creation] workflow
        if (EntityStatus.GROUP_CONFIG_SUCCESSFUL.getCode().equals(groupEntity.getStatus())) {
            executorService.execute(new WorkflowStartRunnable(operator, groupEntity, streamId));
        }
        LOGGER.info("success to update sink info");
        return true;
    }

    @Transactional(rollbackFor = Throwable.class)
    @Override
    public Boolean delete(Integer id, String sinkType, String operator) {
        LOGGER.info("begin to delete sink by id={}, sinkType={}", id, sinkType);
        Preconditions.checkNotNull(id, Constant.ID_IS_EMPTY);
        // Preconditions.checkNotNull(sinkType, Constant.SINK_TYPE_IS_EMPTY);

        StreamSinkEntity entity = sinkMapper.selectByPrimaryKey(id);
        Preconditions.checkNotNull(entity, ErrorCodeEnum.SINK_INFO_NOT_FOUND.getMessage());
        commonOperateService.checkGroupStatus(entity.getInlongGroupId(), operator);

        entity.setPreviousStatus(entity.getStatus());
        entity.setStatus(EntityStatus.DELETED.getCode());
        entity.setIsDeleted(id);
        entity.setModifier(operator);
        entity.setModifyTime(new Date());
        sinkMapper.updateByPrimaryKeySelective(entity);

        sinkFieldMapper.logicDeleteAll(id);

        LOGGER.info("success to delete sink info");
        return true;
    }

    @Override
    public void updateStatus(int id, int status, String log) {
        StreamSinkEntity entity = new StreamSinkEntity();
        entity.setId(id);
        entity.setStatus(status);
        entity.setOperateLog(log);
        sinkMapper.updateStatus(entity);
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public Boolean logicDeleteAll(String groupId, String streamId, String operator) {
        LOGGER.info("begin to logic delete all sink info by groupId={}, streamId={}", groupId, streamId);
        Preconditions.checkNotNull(groupId, Constant.GROUP_ID_IS_EMPTY);
        Preconditions.checkNotNull(streamId, Constant.STREAM_ID_IS_EMPTY);

        // Check if it can be deleted
        commonOperateService.checkGroupStatus(groupId, operator);

        Date now = new Date();
        List<StreamSinkEntity> entityList = sinkMapper.selectByRelatedId(groupId, streamId);
        if (CollectionUtils.isNotEmpty(entityList)) {
            entityList.forEach(entity -> {
                Integer id = entity.getId();
                entity.setPreviousStatus(entity.getStatus());
                entity.setStatus(EntityStatus.DELETED.getCode());
                entity.setIsDeleted(id);
                entity.setModifier(operator);
                entity.setModifyTime(now);

                sinkMapper.deleteByPrimaryKey(id);
                sinkFieldMapper.logicDeleteAll(id);
            });
        }

        LOGGER.info("success to logic delete all sink by groupId={}, streamId={}", groupId, streamId);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public Boolean deleteAll(String groupId, String streamId, String operator) {
        LOGGER.info("begin to delete all sink by groupId={}, streamId={}", groupId, streamId);
        Preconditions.checkNotNull(groupId, Constant.GROUP_ID_IS_EMPTY);
        Preconditions.checkNotNull(streamId, Constant.STREAM_ID_IS_EMPTY);

        // Check if it can be deleted
        commonOperateService.checkGroupStatus(groupId, operator);

        List<StreamSinkEntity> entityList = sinkMapper.selectByRelatedId(groupId, streamId);
        if (CollectionUtils.isNotEmpty(entityList)) {
            entityList.forEach(entity -> {
                sinkMapper.deleteByPrimaryKey(entity.getId());
                sinkFieldMapper.deleteAll(entity.getId());
            });
        }

        LOGGER.info("success to delete all sink by groupId={}, streamId={}", groupId, streamId);
        return true;
    }

    @Override
    public List<String> getExistsStreamIdList(String groupId, String sinkType, List<String> streamIdList) {
        LOGGER.debug("begin to filter stream by groupId={}, type={}, streamId={}", groupId, sinkType, streamIdList);
        if (StringUtils.isEmpty(sinkType) || CollectionUtils.isEmpty(streamIdList)) {
            return Collections.emptyList();
        }

        List<String> resultList = sinkMapper.selectExistsStreamId(groupId, sinkType, streamIdList);
        LOGGER.debug("success to filter stream id list, result streamId={}", resultList);
        return resultList;
    }

    @Override
    public List<String> getSinkTypeList(String groupId, String streamId) {
        LOGGER.debug("begin to get sink type list by groupId={}, streamId={}", groupId, streamId);
        if (StringUtils.isEmpty(streamId)) {
            return Collections.emptyList();
        }

        List<String> resultList = sinkMapper.selectSinkType(groupId, streamId);
        LOGGER.debug("success to get sink type list, result sinkType={}", resultList);
        return resultList;
    }

    @Override
    public Boolean updateAfterApprove(List<SinkApproveDTO> approveList, String operator) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("begin to update sink after approve={}", approveList);
        }
        if (CollectionUtils.isEmpty(approveList)) {
            return true;
        }

        Date now = new Date();
        for (SinkApproveDTO dto : approveList) {
            // According to the sink type, save sink information
            String sinkType = dto.getSinkType();
            Preconditions.checkNotNull(sinkType, Constant.SINK_TYPE_IS_EMPTY);

            StreamSinkEntity entity = new StreamSinkEntity();
            entity.setId(dto.getId());

            int status = (dto.getStatus() == null) ? EntityStatus.SINK_CONFIG_ING.getCode() : dto.getStatus();
            entity.setPreviousStatus(entity.getStatus());
            entity.setStatus(status);
            entity.setModifier(operator);
            entity.setModifyTime(now);
            sinkMapper.updateByPrimaryKeySelective(entity);
        }

        LOGGER.info("success to update sink after approve");
        return true;
    }

    private void checkParams(SinkRequest request) {
        Preconditions.checkNotNull(request, Constant.REQUEST_IS_EMPTY);
        String groupId = request.getInlongGroupId();
        Preconditions.checkNotNull(groupId, Constant.GROUP_ID_IS_EMPTY);
        String streamId = request.getInlongStreamId();
        Preconditions.checkNotNull(streamId, Constant.STREAM_ID_IS_EMPTY);
        String sinkType = request.getSinkType();
        Preconditions.checkNotNull(sinkType, Constant.SINK_TYPE_IS_EMPTY);
    }

    /**
     * Asynchronously initiate a single inlong stream related workflow
     *
     * @see CreateStreamWorkflowDefinition
     */
    class WorkflowStartRunnable implements Runnable {

        private final String operator;
        private final InlongGroupEntity inlongGroupEntity;
        private final String streamId;

        public WorkflowStartRunnable(String operator, InlongGroupEntity inlongGroupEntity, String streamId) {
            this.operator = operator;
            this.inlongGroupEntity = inlongGroupEntity;
            this.streamId = streamId;
        }

        @Override
        public void run() {
            String groupId = inlongGroupEntity.getInlongGroupId();
            LOGGER.info("begin start inlong stream workflow, groupId={}, streamId={}", groupId, streamId);

            InlongGroupInfo groupInfo = CommonBeanUtils.copyProperties(inlongGroupEntity, InlongGroupInfo::new);
            GroupResourceProcessForm form = genGroupResourceProcessForm(groupInfo, streamId);

            workflowService.start(ProcessName.CREATE_STREAM_RESOURCE, operator, form);
            LOGGER.info("success start inlong stream workflow, groupId={}, streamId={}", groupId, streamId);
        }

        /**
         * Generate [Group Resource] form
         */
        private GroupResourceProcessForm genGroupResourceProcessForm(InlongGroupInfo groupInfo, String streamId) {
            GroupResourceProcessForm form = new GroupResourceProcessForm();
            form.setGroupInfo(groupInfo);
            form.setInlongStreamId(streamId);
            return form;
        }
    }

}
