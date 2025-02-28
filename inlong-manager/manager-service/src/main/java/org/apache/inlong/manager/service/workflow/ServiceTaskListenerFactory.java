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

package org.apache.inlong.manager.service.workflow;

import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.PostConstruct;
import lombok.Setter;
import org.apache.commons.collections.MapUtils;
import org.apache.inlong.manager.common.event.EventSelector;
import org.apache.inlong.manager.common.event.task.DataSourceOperateListener;
import org.apache.inlong.manager.common.event.task.QueueOperateListener;
import org.apache.inlong.manager.common.event.task.SortOperateListener;
import org.apache.inlong.manager.common.event.task.StorageOperateListener;
import org.apache.inlong.manager.common.event.task.TaskEventListener;
import org.apache.inlong.manager.common.model.WorkflowContext;
import org.apache.inlong.manager.common.model.definition.ServiceTaskListenerProvider;
import org.apache.inlong.manager.common.model.definition.ServiceTaskType;
import org.apache.inlong.manager.common.plugin.Plugin;
import org.apache.inlong.manager.common.plugin.PluginBinder;
import org.apache.inlong.manager.common.plugin.ProcessPlugin;
import org.apache.inlong.manager.service.thirdpart.hive.CreateHiveTableListener;
import org.apache.inlong.manager.service.thirdpart.hive.CreateHiveTableEventSelector;
import org.apache.inlong.manager.service.thirdpart.mq.CreatePulsarGroupTaskListener;
import org.apache.inlong.manager.service.thirdpart.mq.CreatePulsarResourceTaskListener;
import org.apache.inlong.manager.service.thirdpart.mq.CreateTubeGroupTaskListener;
import org.apache.inlong.manager.service.thirdpart.mq.CreateTubeTopicTaskListener;
import org.apache.inlong.manager.service.thirdpart.mq.PulsarEventSelector;
import org.apache.inlong.manager.service.thirdpart.mq.TubeEventSelector;
import org.apache.inlong.manager.service.thirdpart.sort.PushHiveConfigTaskListener;
import org.apache.inlong.manager.service.thirdpart.sort.ZkSortEventSelector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ServiceTaskListenerFactory implements PluginBinder, ServiceTaskListenerProvider {

    private Map<DataSourceOperateListener, EventSelector> sourceOperateListeners;

    private Map<StorageOperateListener, EventSelector> storageOperateListeners;

    private Map<QueueOperateListener, EventSelector> queueOperateListeners;

    private Map<SortOperateListener, EventSelector> sortOperateListeners;

    @Autowired
    @Setter
    private CreateTubeTopicTaskListener createTubeTopicTaskListener;
    @Autowired
    @Setter
    private CreateTubeGroupTaskListener createTubeGroupTaskListener;
    @Autowired
    @Setter
    private CreatePulsarResourceTaskListener createPulsarResourceTaskListener;
    @Autowired
    @Setter
    private CreatePulsarGroupTaskListener createPulsarGroupTaskListener;

    @Autowired
    @Setter
    private CreateHiveTableListener createHiveTableListener;
    @Autowired
    private CreateHiveTableEventSelector createHiveTableEventSelector;

    @Autowired
    @Setter
    private PushHiveConfigTaskListener pushHiveConfigTaskListener;
    @Autowired
    private ZkSortEventSelector zkSortEventSelector;

    @PostConstruct
    public void init() {
        sourceOperateListeners = new LinkedHashMap<>();
        storageOperateListeners = new LinkedHashMap<>();
        storageOperateListeners.put(createHiveTableListener, createHiveTableEventSelector);
        queueOperateListeners = new LinkedHashMap<>();
        queueOperateListeners.put(createTubeTopicTaskListener, new TubeEventSelector());
        queueOperateListeners.put(createTubeGroupTaskListener, new TubeEventSelector());
        queueOperateListeners.put(createPulsarResourceTaskListener, new PulsarEventSelector());
        queueOperateListeners.put(createPulsarGroupTaskListener, new PulsarEventSelector());
        sortOperateListeners = new LinkedHashMap<>();
        sortOperateListeners.put(pushHiveConfigTaskListener, zkSortEventSelector);
    }

    public void clearListeners() {
        sourceOperateListeners = new LinkedHashMap<>();
        storageOperateListeners = new LinkedHashMap<>();
        queueOperateListeners = new LinkedHashMap<>();
        sortOperateListeners = new LinkedHashMap<>();
    }

    @Override
    public List<TaskEventListener> get(WorkflowContext workflowContext, ServiceTaskType serviceTaskType) {
        switch (serviceTaskType) {
            case INIT_MQ:
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
            case INIT_STORAGE:
                List<StorageOperateListener> storageOperateListeners = getStorageOperateListener(workflowContext);
                return Lists.newArrayList(storageOperateListeners);
            default:
                throw new IllegalArgumentException(String.format("UnSupport ServiceTaskType %s", serviceTaskType));
        }
    }

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

    public List<StorageOperateListener> getStorageOperateListener(WorkflowContext context) {
        List<StorageOperateListener> listeners = new ArrayList<>();
        for (Map.Entry<StorageOperateListener, EventSelector> entry : storageOperateListeners.entrySet()) {
            EventSelector selector = entry.getValue();
            if (selector != null && selector.accept(context)) {
                listeners.add(entry.getKey());
            }
        }
        return listeners;
    }

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
        Map<StorageOperateListener, EventSelector> pluginStorageOperateListeners =
                processPlugin.createStorageOperateListeners();
        if (MapUtils.isNotEmpty(pluginStorageOperateListeners)) {
            storageOperateListeners.putAll(pluginStorageOperateListeners);
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
    }
}
