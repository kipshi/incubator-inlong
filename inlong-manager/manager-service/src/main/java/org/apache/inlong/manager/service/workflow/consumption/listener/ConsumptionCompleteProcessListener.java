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

package org.apache.inlong.manager.service.workflow.consumption.listener;

import lombok.extern.slf4j.Slf4j;
import org.apache.inlong.common.pojo.dataproxy.PulsarClusterInfo;
import org.apache.inlong.manager.common.beans.ClusterBean;
import org.apache.inlong.manager.common.enums.Constant;
import org.apache.inlong.manager.common.enums.ConsumptionStatus;
import org.apache.inlong.manager.common.exceptions.WorkflowListenerException;
import org.apache.inlong.manager.common.pojo.group.InlongGroupInfo;
import org.apache.inlong.manager.common.pojo.pulsar.PulsarTopicBean;
import org.apache.inlong.manager.common.pojo.tubemq.AddTubeConsumeGroupRequest;
import org.apache.inlong.manager.common.pojo.workflow.form.NewConsumptionProcessForm;
import org.apache.inlong.manager.common.util.Preconditions;
import org.apache.inlong.manager.dao.entity.ConsumptionEntity;
import org.apache.inlong.manager.dao.mapper.ConsumptionEntityMapper;
import org.apache.inlong.manager.service.CommonOperateService;
import org.apache.inlong.manager.service.core.InlongGroupService;
import org.apache.inlong.manager.service.thirdparty.mq.PulsarOptService;
import org.apache.inlong.manager.service.thirdparty.mq.TubeMqOptService;
import org.apache.inlong.manager.service.thirdparty.mq.util.PulsarUtils;
import org.apache.inlong.manager.workflow.WorkflowContext;
import org.apache.inlong.manager.workflow.event.ListenerResult;
import org.apache.inlong.manager.workflow.event.process.ProcessEvent;
import org.apache.inlong.manager.workflow.event.process.ProcessEventListener;
import org.apache.pulsar.client.admin.PulsarAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Added data consumption process complete archive event listener
 */
@Slf4j
@Component
public class ConsumptionCompleteProcessListener implements ProcessEventListener {

    @Autowired
    private PulsarOptService pulsarMqOptService;
    @Autowired
    private ClusterBean clusterBean;
    @Autowired
    private CommonOperateService commonOperateService;
    @Autowired
    private InlongGroupService groupService;
    @Autowired
    private ConsumptionEntityMapper consumptionMapper;
    @Autowired
    private TubeMqOptService tubeMqOptService;

    @Override
    public ProcessEvent event() {
        return ProcessEvent.COMPLETE;
    }

    @Override
    public ListenerResult listen(WorkflowContext context) throws WorkflowListenerException {
        NewConsumptionProcessForm consumptionForm = (NewConsumptionProcessForm) context.getProcessForm();

        // Real-time query of consumption information
        Integer consumptionId = consumptionForm.getConsumptionInfo().getId();
        ConsumptionEntity entity = consumptionMapper.selectByPrimaryKey(consumptionId);
        if (entity == null) {
            throw new WorkflowListenerException("consumption not exits for id=" + consumptionId);
        }

        String mqType = entity.getMiddlewareType();
        if (Constant.MIDDLEWARE_TUBE.equals(mqType)) {
            this.createTubeConsumerGroup(entity);
            return ListenerResult.success("Create Tube consumer group successful");
        } else if (Constant.MIDDLEWARE_PULSAR.equals(mqType) || Constant.MIDDLEWARE_TDMQ_PULSAR.equals(mqType)) {
            this.createPulsarTopicMessage(entity);
        } else {
            throw new WorkflowListenerException("middleware type [" + mqType + "] not supported");
        }

        this.updateConsumerInfo(consumptionId, entity.getConsumerGroupId());
        return ListenerResult.success("create Tube /Pulsar consumer group successful");
    }

    /**
     * Update consumption after approve
     */
    private void updateConsumerInfo(Integer consumptionId, String consumerGroupId) {
        ConsumptionEntity update = new ConsumptionEntity();
        update.setId(consumptionId);
        update.setStatus(ConsumptionStatus.APPROVED.getStatus());
        update.setConsumerGroupId(consumerGroupId);
        update.setModifyTime(new Date());
        consumptionMapper.updateByPrimaryKeySelective(update);
    }

    /**
     * Create Pulsar consumption information, including cross-regional cycle creation of consumption groups
     */
    private void createPulsarTopicMessage(ConsumptionEntity entity) {
        String groupId = entity.getInlongGroupId();
        InlongGroupInfo groupInfo = groupService.get(groupId);
        Preconditions.checkNotNull(groupInfo, "inlong group not found for groupId=" + groupId);
        String mqResourceObj = groupInfo.getMqResourceObj();
        Preconditions.checkNotNull(mqResourceObj, "mq resource cannot empty for groupId=" + groupId);
        PulsarClusterInfo globalCluster = commonOperateService.getPulsarClusterInfo(entity.getMiddlewareType());
        try (PulsarAdmin pulsarAdmin = PulsarUtils.getPulsarAdmin(globalCluster)) {
            PulsarTopicBean topicMessage = new PulsarTopicBean();
            String tenant = clusterBean.getDefaultTenant();
            topicMessage.setTenant(tenant);
            topicMessage.setNamespace(mqResourceObj);

            // If cross-regional replication is started, each cluster needs to create consumer groups in cycles
            String consumerGroup = entity.getConsumerGroupId();
            List<String> clusters = PulsarUtils.getPulsarClusters(pulsarAdmin);
            List<String> topics = Arrays.asList(entity.getTopic().split(","));
            this.createPulsarSubscription(pulsarAdmin, consumerGroup, topicMessage, clusters, topics, globalCluster);
        } catch (Exception e) {
            log.error("create pulsar topic failed", e);
            throw new WorkflowListenerException("failed to create pulsar topic for groupId=" + groupId + ", reason: "
                    + e.getMessage());
        }
    }

    private void createPulsarSubscription(PulsarAdmin globalPulsarAdmin, String subscription, PulsarTopicBean topicBean,
            List<String> clusters, List<String> topics, PulsarClusterInfo globalCluster) {
        try {
            for (String cluster : clusters) {
                String serviceUrl = PulsarUtils.getServiceUrl(globalPulsarAdmin, cluster);
                PulsarClusterInfo pulsarClusterInfo = PulsarClusterInfo.builder()
                        .token(globalCluster.getToken()).adminUrl(serviceUrl).build();
                try (PulsarAdmin pulsarAdmin = PulsarUtils.getPulsarAdmin(pulsarClusterInfo)) {
                    pulsarMqOptService.createSubscriptions(pulsarAdmin, subscription, topicBean, topics);
                }
            }
        } catch (Exception e) {
            log.error("create pulsar consumer group failed", e);
            throw new WorkflowListenerException("failed to create pulsar consumer group");
        }
    }

    /**
     * Create tube consumer group
     */
    private void createTubeConsumerGroup(ConsumptionEntity consumption) {
        AddTubeConsumeGroupRequest addTubeConsumeGroupRequest = new AddTubeConsumeGroupRequest();
        addTubeConsumeGroupRequest.setClusterId(1); // TODO is cluster id needed?
        addTubeConsumeGroupRequest.setCreateUser(consumption.getCreator());
        AddTubeConsumeGroupRequest.GroupNameJsonSetBean bean = new AddTubeConsumeGroupRequest.GroupNameJsonSetBean();
        bean.setTopicName(consumption.getTopic());
        bean.setGroupName(consumption.getConsumerGroupId());
        addTubeConsumeGroupRequest.setGroupNameJsonSet(Collections.singletonList(bean));

        try {
            tubeMqOptService.createNewConsumerGroup(addTubeConsumeGroupRequest);
        } catch (Exception e) {
            throw new WorkflowListenerException("failed to create tube consumer group: " + addTubeConsumeGroupRequest);
        }
    }

    @Override
    public boolean async() {
        return false;
    }

}
