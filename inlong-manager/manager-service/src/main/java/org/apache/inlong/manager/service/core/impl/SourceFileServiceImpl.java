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

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.apache.commons.collections.CollectionUtils;
import org.apache.inlong.manager.common.enums.Constant;
import org.apache.inlong.manager.common.enums.EntityStatus;
import org.apache.inlong.manager.common.enums.ErrorCodeEnum;
import org.apache.inlong.manager.common.exceptions.BusinessException;
import org.apache.inlong.manager.common.pojo.source.SourceFileBasicInfo;
import org.apache.inlong.manager.common.pojo.source.SourceFileDetailInfo;
import org.apache.inlong.manager.common.pojo.source.SourceFileDetailListVO;
import org.apache.inlong.manager.common.pojo.source.SourceFileDetailPageRequest;
import org.apache.inlong.manager.common.util.CommonBeanUtils;
import org.apache.inlong.manager.common.util.Preconditions;
import org.apache.inlong.manager.dao.entity.InlongGroupEntity;
import org.apache.inlong.manager.dao.entity.SourceFileBasicEntity;
import org.apache.inlong.manager.dao.entity.SourceFileDetailEntity;
import org.apache.inlong.manager.dao.mapper.InlongGroupEntityMapper;
import org.apache.inlong.manager.dao.mapper.SourceFileBasicEntityMapper;
import org.apache.inlong.manager.dao.mapper.SourceFileDetailEntityMapper;
import org.apache.inlong.manager.service.core.SourceFileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * File data source service layer implementation
 */
@Deprecated
@Service
public class SourceFileServiceImpl implements SourceFileService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SourceFileServiceImpl.class);

    @Autowired
    private SourceFileBasicEntityMapper fileBasicMapper;
    @Autowired
    private SourceFileDetailEntityMapper fileDetailMapper;
    @Autowired
    private InlongGroupEntityMapper groupMapper;

    @Override
    public Integer saveBasic(SourceFileBasicInfo basicInfo, String operator) {
        LOGGER.info("begin to save file data source basic={}", basicInfo);
        Preconditions.checkNotNull(basicInfo, "file data source basic is empty");
        String groupId = basicInfo.getInlongGroupId();
        String streamId = basicInfo.getInlongStreamId();
        Preconditions.checkNotNull(groupId, Constant.GROUP_ID_IS_EMPTY);
        Preconditions.checkNotNull(streamId, Constant.STREAM_ID_IS_EMPTY);

        // Check if it can be added
        this.checkGroupIsTempStatus(groupId);

        // Each groupId + streamId has only 1 valid basic information
        SourceFileBasicEntity exist = fileBasicMapper.selectByIdentifier(groupId, streamId);
        if (exist != null) {
            LOGGER.error("file data source basic already exists, please check");
            throw new BusinessException(ErrorCodeEnum.SOURCE_DUPLICATE);
        }

        SourceFileBasicEntity entity = CommonBeanUtils.copyProperties(basicInfo, SourceFileBasicEntity::new);
        entity.setCreator(operator);
        entity.setModifier(operator);
        entity.setCreateTime(new Date());
        fileBasicMapper.insertSelective(entity);

        LOGGER.info("success to save file data source basic");
        return entity.getId();
    }

    @Override
    public SourceFileBasicInfo getBasicByIdentifier(String groupId, String streamId) {
        Preconditions.checkNotNull(groupId, Constant.GROUP_ID_IS_EMPTY);
        Preconditions.checkNotNull(streamId, Constant.STREAM_ID_IS_EMPTY);

        SourceFileBasicEntity entity = fileBasicMapper.selectByIdentifier(groupId, streamId);
        SourceFileBasicInfo basicInfo = new SourceFileBasicInfo();
        if (entity == null) {
            LOGGER.error("file data source basic not found by streamId={}", streamId);
            // throw new BusinessException(ErrorCodeEnum.DATA_SOURCE_BASIC_NOTFOUND);
            return basicInfo;
        }
        CommonBeanUtils.copyProperties(entity, basicInfo);

        LOGGER.debug("success to get file data source basic");
        return basicInfo;
    }

    @Override
    public boolean updateBasic(SourceFileBasicInfo basicInfo, String operator) {
        LOGGER.info("begin to update file data source basic={}", basicInfo);
        Preconditions.checkNotNull(basicInfo, "file data source basic is empty");

        // The groupId may be modified, it is necessary to determine whether the inlong group status of
        // the modified groupId supports modification
        this.checkGroupIsTempStatus(basicInfo.getInlongGroupId());

        // If id is empty, add
        if (basicInfo.getId() == null) {
            this.saveBasic(basicInfo, operator);
        } else {
            SourceFileBasicEntity basicEntity = fileBasicMapper.selectByPrimaryKey(basicInfo.getId());
            if (basicEntity == null) {
                LOGGER.error("file data source basic not found by id={}, update failed", basicInfo.getId());
                throw new BusinessException(ErrorCodeEnum.SOURCE_BASIC_NOT_FOUND);
            }

            BeanUtils.copyProperties(basicInfo, basicEntity);
            basicEntity.setModifier(operator);
            fileBasicMapper.updateByPrimaryKeySelective(basicEntity);
        }

        LOGGER.info("success to update file data source basic");
        return true;
    }

    @Transactional(rollbackFor = Throwable.class)
    @Override
    public boolean logicDeleteBasic(Integer id, String operator) {
        LOGGER.info("begin to delete file data source basic, id={}", id);
        Preconditions.checkNotNull(id, "file data source basic's id is null");

        SourceFileBasicEntity entity = fileBasicMapper.selectByPrimaryKey(id);
        if (entity == null) {
            LOGGER.error("file data source basic not found by id={}, delete failed", id);
            throw new BusinessException(ErrorCodeEnum.SOURCE_BASIC_NOT_FOUND);
        }

        String groupId = entity.getInlongGroupId();
        String streamId = entity.getInlongStreamId();
        // Check if it can be deleted
        this.checkGroupIsTempStatus(groupId);

        // If there are related data source details, it is not allowed to delete
        List<SourceFileDetailEntity> detailEntities = fileDetailMapper.selectByIdentifier(groupId, streamId);
        if (CollectionUtils.isNotEmpty(detailEntities)) {
            LOGGER.error("the data source basic have [{}] details, delete failed", detailEntities.size());
            throw new BusinessException(ErrorCodeEnum.SOURCE_BASIC_DELETE_HAS_DETAIL);
        }

        entity.setIsDeleted(1);
        entity.setModifier(operator);
        int resultCount = fileBasicMapper.updateByPrimaryKey(entity);

        LOGGER.info("success to delete file data source basic");
        return resultCount >= 0;
    }

    @Override
    public Integer saveDetail(SourceFileDetailInfo detailInfo, String operator) {
        LOGGER.info("begin to save file data source detail={}", detailInfo);
        Preconditions.checkNotNull(detailInfo, "file data source detail is empty");
        Preconditions.checkNotNull(detailInfo.getInlongGroupId(), Constant.GROUP_ID_IS_EMPTY);
        Preconditions.checkNotNull(detailInfo.getInlongStreamId(), Constant.STREAM_ID_IS_EMPTY);

        // Check if it can be added
        InlongGroupEntity inlongGroupEntity = this.checkGroupIsTempStatus(detailInfo.getInlongGroupId());

        // If there are data sources under the same groupId, streamId, ip, username, the addition fails
        String groupId = detailInfo.getInlongGroupId();
        String streamId = detailInfo.getInlongStreamId();
        String ip = detailInfo.getIp();
        String username = detailInfo.getUsername();
        Integer count = fileDetailMapper.selectDetailExist(groupId, streamId, ip, username);
        if (count > 0) {
            LOGGER.error("file data source already exists: groupId=" + groupId + ", streamId=" + streamId
                    + ", ip=" + ip + ", username=" + username);
            throw new BusinessException(ErrorCodeEnum.SOURCE_DUPLICATE);
        }

        detailInfo.setStatus(EntityStatus.AGENT_ADD.getCode());
        SourceFileDetailEntity detailEntity = CommonBeanUtils.copyProperties(detailInfo, SourceFileDetailEntity::new);
        detailEntity.setCreator(operator);
        detailEntity.setModifier(operator);
        Date now = new Date();
        detailEntity.setCreateTime(now);
        detailEntity.setModifyTime(now);
        fileDetailMapper.insertSelective(detailEntity);

        LOGGER.info("success to save file data source detail");
        return detailEntity.getId();
    }

    @Override
    public SourceFileDetailInfo getDetailById(Integer id) {
        Preconditions.checkNotNull(id, "file data source detail's id is null");

        SourceFileDetailEntity entity = fileDetailMapper.selectByPrimaryKey(id);
        if (entity == null) {
            LOGGER.error("file data source detail not found by id={}", id);
            throw new BusinessException(ErrorCodeEnum.SOURCE_DETAIL_NOT_FOUND);
        }
        SourceFileDetailInfo detailInfo = CommonBeanUtils.copyProperties(entity, SourceFileDetailInfo::new);

        LOGGER.debug("success to get file data source detail");
        return detailInfo;
    }

    @Override
    public List<SourceFileDetailInfo> listDetailByIdentifier(String groupId, String streamId) {
        Preconditions.checkNotNull(groupId, Constant.GROUP_ID_IS_EMPTY);

        List<SourceFileDetailEntity> entities = fileDetailMapper.selectByIdentifier(groupId, streamId);
        if (CollectionUtils.isEmpty(entities)) {
            LOGGER.warn("file data source detail not found");
            // throw new BusinessException(ErrorCodeEnum.DATA_SOURCE_DETAIL_NOTFOUND);
            return Collections.emptyList();
        }

        List<SourceFileDetailInfo> infoList = CommonBeanUtils.copyListProperties(entities, SourceFileDetailInfo::new);
        LOGGER.debug("success to list file data source detail");
        return infoList;
    }

    @Override
    public PageInfo<SourceFileDetailListVO> listByCondition(SourceFileDetailPageRequest request) {
        PageHelper.startPage(request.getPageNum(), request.getPageSize());
        Page<SourceFileDetailEntity> page = (Page<SourceFileDetailEntity>) fileDetailMapper.selectByCondition(request);
        List<SourceFileDetailListVO> detailList = CommonBeanUtils.copyListProperties(page, SourceFileDetailListVO::new);

        // Encapsulate the paging query results into the PageInfo object to obtain related paging information
        PageInfo<SourceFileDetailListVO> pageInfo = new PageInfo<>(detailList);
        pageInfo.setTotal(page.getTotal());

        LOGGER.debug("success to list file data source detail");
        return pageInfo;
    }

    @Transactional(rollbackFor = Throwable.class)
    @Override
    public boolean updateDetail(SourceFileDetailInfo detailInfo, String operator) {
        LOGGER.info("begin to update file data source detail={}", detailInfo);
        Preconditions.checkNotNull(detailInfo, "file data source detail is empty");

        Integer id = detailInfo.getId();
        Preconditions.checkNotNull(id, Constant.ID_IS_EMPTY);

        SourceFileDetailEntity entity = fileDetailMapper.selectByPrimaryKey(id);
        if (entity == null) {
            LOGGER.error("file data source detail not found by id=" + id);
            throw new BusinessException(ErrorCodeEnum.SOURCE_DETAIL_NOT_FOUND);
        }

        // After the approval is passed, the status needs to be revised to be revised to be issued: 205
        this.checkGroupIsTempStatus(detailInfo.getInlongGroupId());
        detailInfo.setStatus(EntityStatus.AGENT_ADD.getCode());

        SourceFileDetailEntity updateEntity = CommonBeanUtils.copyProperties(detailInfo, SourceFileDetailEntity::new);
        updateEntity.setModifier(operator);
        updateEntity.setModifyTime(new Date());
        fileDetailMapper.updateByPrimaryKeySelective(updateEntity);

        LOGGER.info("success to update file data source detail");
        return true;
    }

    @Transactional(rollbackFor = Throwable.class)
    @Override
    public boolean logicDeleteDetail(Integer id, String operator) {
        LOGGER.info("begin to delete file data source detail, id={}", id);
        Preconditions.checkNotNull(id, "file data source detail's id is null");

        SourceFileDetailEntity entity = fileDetailMapper.selectByPrimaryKey(id);
        if (entity == null) {
            LOGGER.error("file data source detail not found by id={}", id);
            throw new BusinessException(ErrorCodeEnum.SOURCE_DETAIL_NOT_FOUND);
        }

        // Check if it can be deleted
        InlongGroupEntity bizEntity = this.checkGroupIsTempStatus(entity.getInlongGroupId());

        // After the approval is passed, the status needs to be modified to delete to be issued: 204
        if (EntityStatus.GROUP_CONFIG_SUCCESSFUL.getCode().equals(bizEntity.getStatus())) {
            entity.setPreviousStatus(entity.getStatus());
            entity.setStatus(EntityStatus.AGENT_DELETE.getCode());
        } else {
            entity.setPreviousStatus(entity.getStatus());
            entity.setStatus(EntityStatus.AGENT_DISABLE.getCode());
        }

        entity.setIsDeleted(EntityStatus.IS_DELETED.getCode());
        entity.setModifier(operator);
        int resultCount = fileDetailMapper.updateByPrimaryKey(entity);

        LOGGER.info("success to delete file data source detail");
        return resultCount >= 0;
    }

    @Transactional(rollbackFor = Throwable.class)
    @Override
    public boolean deleteAllByIdentifier(String groupId, String streamId) {
        LOGGER.info("begin delete all file basic and detail by groupId={}, streamId={}", groupId, streamId);
        Preconditions.checkNotNull(groupId, Constant.GROUP_ID_IS_EMPTY);
        Preconditions.checkNotNull(streamId, Constant.STREAM_ID_IS_EMPTY);

        // Check if it can be deleted
        this.checkGroupIsTempStatus(groupId);

        fileBasicMapper.deleteByIdentifier(groupId, streamId);
        fileDetailMapper.deleteByIdentifier(groupId, streamId);
        LOGGER.info("success delete all file basic and detail");
        return true;
    }

    @Transactional(rollbackFor = Throwable.class)
    @Override
    public boolean logicDeleteAllByIdentifier(String groupId, String streamId, String operator) {
        LOGGER.info("begin logic delete all file basic and detail by groupId={}, streamId={}", groupId, streamId);
        Preconditions.checkNotNull(groupId, Constant.GROUP_ID_IS_EMPTY);
        Preconditions.checkNotNull(streamId, Constant.STREAM_ID_IS_EMPTY);

        // Check if it can be deleted
        this.checkGroupIsTempStatus(groupId);

        fileBasicMapper.logicDeleteByIdentifier(groupId, streamId, operator);
        fileDetailMapper.logicDeleteByIdentifier(groupId, streamId, operator);
        LOGGER.info("success logic delete all file basic and detail");
        return true;
    }

    /**
     * Check whether the inlong group status is temporary
     *
     * @param groupId Inlong group id
     * @return Inlong group entity for caller reuse
     */
    private InlongGroupEntity checkGroupIsTempStatus(String groupId) {
        InlongGroupEntity inlongGroupEntity = groupMapper.selectByGroupId(groupId);
        Preconditions.checkNotNull(inlongGroupEntity, "groupId is invalid");
        // Add/modify/delete is not allowed under certain inlong group status
        if (EntityStatus.GROUP_TEMP_STATUS.contains(inlongGroupEntity.getStatus())) {
            LOGGER.error("inlong group status was not allowed to add/update/delete data source info");
            throw new BusinessException(ErrorCodeEnum.SOURCE_OPT_NOT_ALLOWED);
        }

        return inlongGroupEntity;
    }

}