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

package org.apache.inlong.manager.service.workflow.listener;

import com.google.common.collect.Lists;
import lombok.Data;
import org.apache.commons.collections.MapUtils;
import org.apache.inlong.manager.common.pojo.workflow.form.process.ProcessForm;
import org.apache.inlong.manager.common.pojo.workflow.form.process.StreamResourceProcessForm;
import org.apache.inlong.manager.service.mq.CreatePulsarSubscriptionTaskListener;
import org.apache.inlong.manager.service.mq.CreatePulsarTopicTaskListener;
import org.apache.inlong.manager.service.mq.DeletePulsarTopicTaskListener;
import org.apache.inlong.manager.service.mq.PulsarTopicCreateSelector;
import org.apache.inlong.manager.service.mq.PulsarTopicDeleteSelector;
import org.apache.inlong.manager.service.resource.StreamSinkResourceListener;
import org.apache.inlong.manager.service.sort.CreateStreamSortConfigListener;
import org.apache.inlong.manager.service.sort.ZookeeperDisabledSelector;
import org.apache.inlong.manager.workflow.WorkflowContext;
import org.apache.inlong.manager.workflow.definition.ServiceTaskListenerProvider;
import org.apache.inlong.manager.workflow.definition.ServiceTaskType;
import org.apache.inlong.manager.workflow.event.EventSelector;
import org.apache.inlong.manager.workflow.event.task.DataSourceOperateListener;
import org.apache.inlong.manager.workflow.event.task.QueueOperateListener;
import org.apache.inlong.manager.workflow.event.task.SinkOperateListener;
import org.apache.inlong.manager.workflow.event.task.SortOperateListener;
import org.apache.inlong.manager.workflow.plugin.Plugin;
import org.apache.inlong.manager.workflow.plugin.PluginBinder;
import org.apache.inlong.manager.workflow.plugin.ProcessPlugin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@Component
public class StreamTaskListenerFactory implements PluginBinder, ServiceTaskListenerProvider {

    private Map<DataSourceOperateListener, EventSelector> sourceOperateListeners;

    private Map<QueueOperateListener, EventSelector> queueOperateListeners;

    private Map<SortOperateListener, EventSelector> sortOperateListeners;

    private Map<SinkOperateListener, EventSelector> sinkOperateListeners;

    @Autowired
    private CreatePulsarTopicTaskListener createPulsarTopicTaskListener;
    @Autowired
    private CreatePulsarSubscriptionTaskListener createPulsarSubscriptionTaskListener;
    @Autowired
    private DeletePulsarTopicTaskListener deletePulsarTopicTaskListener;
    @Autowired
    private CreateStreamSortConfigListener createSortConfigListener;
    @Autowired
    private StreamSinkResourceListener sinkResourceListener;

    @PostConstruct
    public void init() {
        sourceOperateListeners = new LinkedHashMap<>();
        queueOperateListeners = new LinkedHashMap<>();
        queueOperateListeners.put(createPulsarTopicTaskListener, new PulsarTopicCreateSelector());
        queueOperateListeners.put(createPulsarSubscriptionTaskListener, new PulsarTopicCreateSelector());
        queueOperateListeners.put(deletePulsarTopicTaskListener, new PulsarTopicDeleteSelector());
        sortOperateListeners = new LinkedHashMap<>();
        sortOperateListeners.put(createSortConfigListener, new ZookeeperDisabledSelector());
        sinkOperateListeners = new LinkedHashMap<>();
        sinkOperateListeners.put(sinkResourceListener, context -> {
            ProcessForm processForm = context.getProcessForm();
            return processForm instanceof StreamResourceProcessForm;
        });
    }

    @Override
    public Iterable get(WorkflowContext workflowContext, ServiceTaskType serviceTaskType) {
        switch (serviceTaskType) {
            case INIT_MQ:
            case DELETE_MQ:
                List<QueueOperateListener> queueOperateListeners = getQueueOperateListener(workflowContext);
                return Lists.newArrayList(queueOperateListeners);
            case INIT_SORT:
            case STOP_SORT:
            case RESTART_SORT:
            case DELETE_SORT:
                List<SortOperateListener> sortOperateListeners = getSortOperateListener(workflowContext);
                return Lists.newArrayList(sortOperateListeners);
            case INIT_SOURCE:
            case STOP_SOURCE:
            case RESTART_SOURCE:
            case DELETE_SOURCE:
                List<DataSourceOperateListener> sourceOperateListeners = getSourceOperateListener(workflowContext);
                return Lists.newArrayList(sourceOperateListeners);
            case INIT_SINK:
                List<SinkOperateListener> sinkOperateListeners = getSinkOperateListener(workflowContext);
                return Lists.newArrayList(sinkOperateListeners);
            default:
                throw new IllegalArgumentException(String.format("UnSupport ServiceTaskType %s", serviceTaskType));
        }
    }

    /**
     * Get data source operate listener list.
     */
    public List<DataSourceOperateListener> getSourceOperateListener(WorkflowContext context) {
        List<DataSourceOperateListener> listeners = new ArrayList<>();
        for (Map.Entry<DataSourceOperateListener, EventSelector> entry : sourceOperateListeners.entrySet()) {
            EventSelector selector = entry.getValue();
            if (selector != null && selector.accept(context)) {
                listeners.add(entry.getKey());
            }
        }
        return listeners;
    }

    /**
     * Get queue operate listener list.
     */
    public List<QueueOperateListener> getQueueOperateListener(WorkflowContext context) {
        List<QueueOperateListener> listeners = new ArrayList<>();
        for (Map.Entry<QueueOperateListener, EventSelector> entry : queueOperateListeners.entrySet()) {
            EventSelector selector = entry.getValue();
            if (selector != null && selector.accept(context)) {
                listeners.add(entry.getKey());
            }
        }
        return listeners;
    }

    /**
     * Get sort operate listener list.
     */
    public List<SortOperateListener> getSortOperateListener(WorkflowContext context) {
        List<SortOperateListener> listeners = new ArrayList<>();
        for (Map.Entry<SortOperateListener, EventSelector> entry : sortOperateListeners.entrySet()) {
            EventSelector selector = entry.getValue();
            if (selector != null && selector.accept(context)) {
                listeners.add(entry.getKey());
            }
        }
        return listeners;
    }

    /**
     * Get sink operate listener list.
     */
    public List<SinkOperateListener> getSinkOperateListener(WorkflowContext context) {
        List<SinkOperateListener> listeners = new ArrayList<>();
        for (Map.Entry<SinkOperateListener, EventSelector> entry : sinkOperateListeners.entrySet()) {
            EventSelector selector = entry.getValue();
            if (selector != null && selector.accept(context)) {
                listeners.add(entry.getKey());
            }
        }
        return listeners;
    }

    @Override
    public void acceptPlugin(Plugin plugin) {
        if (!(plugin instanceof ProcessPlugin)) {
            return;
        }
        ProcessPlugin processPlugin = (ProcessPlugin) plugin;
        Map<DataSourceOperateListener, EventSelector> pluginDsOperateListeners =
                processPlugin.createSourceOperateListeners();
        if (MapUtils.isNotEmpty(pluginDsOperateListeners)) {
            sourceOperateListeners.putAll(processPlugin.createSourceOperateListeners());
        }
        Map<QueueOperateListener, EventSelector> pluginQueueOperateListeners =
                processPlugin.createQueueOperateListeners();
        if (MapUtils.isNotEmpty(pluginQueueOperateListeners)) {
            queueOperateListeners.putAll(pluginQueueOperateListeners);
        }
        Map<SortOperateListener, EventSelector> pluginSortOperateListeners =
                processPlugin.createSortOperateListeners();
        if (MapUtils.isNotEmpty(pluginSortOperateListeners)) {
            sortOperateListeners.putAll(pluginSortOperateListeners);
        }
        Map<SinkOperateListener, EventSelector> pluginSinkOperateListeners = processPlugin.createSinkOperateListeners();
        if (MapUtils.isNotEmpty(pluginSinkOperateListeners)) {
            sinkOperateListeners.putAll(pluginSinkOperateListeners);
        }
    }
}
