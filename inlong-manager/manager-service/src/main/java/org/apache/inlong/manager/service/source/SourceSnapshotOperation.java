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

package org.apache.inlong.manager.service.source;

import com.google.common.base.Objects;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.inlong.common.pojo.agent.TaskSnapshotMessage;
import org.apache.inlong.common.pojo.agent.TaskSnapshotRequest;
import org.apache.inlong.manager.common.enums.SourceState;
import org.apache.inlong.manager.dao.entity.StreamSourceEntity;
import org.apache.inlong.manager.dao.mapper.StreamSourceEntityMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy;
import java.util.concurrent.TimeUnit;

/**
 * Operate the source snapshot
 */
@Service
public class SourceSnapshotOperation implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(SourceSnapshotOperation.class);

    /**
     * Cache the task ip and task status, the key is task ip
     */
    private Cache<String, ConcurrentHashMap<Integer, Integer>> agentTaskCache = CacheBuilder.newBuilder()
            .maximumSize(1000).expireAfterWrite(30, TimeUnit.SECONDS).build(
                    new CacheLoader<String, ConcurrentHashMap<Integer, Integer>>() {
                        @Override
                        public ConcurrentHashMap<Integer, Integer> load(String agentIp) throws Exception {
                            List<StreamSourceEntity> sourceEntities = sourceMapper.selectByAgentIp(agentIp);
                            if (CollectionUtils.isEmpty(sourceEntities)) {
                                return null;
                            } else {
                                ConcurrentHashMap<Integer, Integer> tmpMap = new ConcurrentHashMap<>();
                                for (StreamSourceEntity entity : sourceEntities) {
                                    tmpMap.put(entity.getId(), entity.getStatus());
                                }
                                return tmpMap;
                            }
                        }
                    });

    public final ExecutorService executorService = new ThreadPoolExecutor(
            1,
            1,
            10L,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(100),
            new ThreadFactoryBuilder().setNameFormat("stream-source-snapshot-%s").build(),
            new CallerRunsPolicy());

    @Autowired
    private StreamSourceEntityMapper sourceMapper;

    /**
     * The queue for transfer source snapshot
     */
    private LinkedBlockingQueue<TaskSnapshotRequest> snapshotQueue = null;

    @Value("${stream.source.snapshot.queue.size:10000}")
    private int queueSize = 10000;

    private volatile boolean isClose = false;

    /**
     * Start a thread to operate source snapshot after the app started.
     */
    @PostConstruct
    private void startSaveSnapshotTask() {
        if (snapshotQueue == null) {
            snapshotQueue = new LinkedBlockingQueue<>(queueSize);
        }
        SaveSnapshotTaskRunnable taskRunnable = new SaveSnapshotTaskRunnable();
        this.executorService.execute(taskRunnable);
        LOGGER.info("source snapshot operate thread started successfully");
    }

    /**
     * Put snapshot into data queue
     */
    public Boolean snapshot(TaskSnapshotRequest request) {
        if (request == null) {
            return true;
        }

        String agentIp = request.getAgentIp();
        List<TaskSnapshotMessage> snapshotList = request.getSnapshotList();
        if (CollectionUtils.isEmpty(snapshotList)) {
            LOGGER.info("receive snapshot from ip={}, but snapshot list is empty", agentIp);
            return true;
        }
        LOGGER.debug("receive snapshot from ip={}, msg size={}", agentIp, snapshotList.size());

        try {
            // Offer the request of snapshot to the queue, and another thread will parse the data in the queue.
            snapshotQueue.offer(request);

            // Modify the task status based on the tasks reported in the snapshot and the tasks in the cache.
            ConcurrentHashMap<Integer, Integer> idStatusMap = agentTaskCache.getIfPresent(agentIp);
            if (MapUtils.isEmpty(idStatusMap)) {
                LOGGER.info("success report snapshot for ip={}, task status cache is null", agentIp);
                return true;
            }
            boolean isInvalid = false;
            Set<Integer> currentTaskIdSet = new HashSet<>();
            for (TaskSnapshotMessage snapshot : snapshotList) {
                Integer id = snapshot.getJobId();
                if (id == null) {
                    continue;
                }

                currentTaskIdSet.add(id);
                // Update the status from temporary to normal
                Integer status = idStatusMap.get(id);
                if (SourceState.TEMP_TO_NORMAL.contains(status)) {
                    isInvalid = true;
                    StreamSourceEntity source = sourceMapper.selectByIdForUpdate(id);
                    sourceMapper.updateStatus(id, SourceState.SOURCE_NORMAL.getCode(), source.getModifyTime());
                }
            }

            // If the id in the snapshot does not contain pending deletion or pending freezing tasks,
            // then update the status to disable or frozen.
            for (Entry<Integer, Integer> entry : idStatusMap.entrySet()) {
                Integer cacheId = entry.getKey();
                Integer cacheStatus = entry.getValue();
                if (!currentTaskIdSet.contains(cacheId)) {
                    StreamSourceEntity source = sourceMapper.selectByIdForUpdate(cacheId);
                    if (Objects.equal(cacheStatus, SourceState.BEEN_ISSUED_DELETE.getCode())) {
                        isInvalid = true;
                        sourceMapper.updateStatus(cacheId, SourceState.SOURCE_DISABLE.getCode(),
                                source.getModifyTime());
                    } else if (Objects.equal(cacheStatus, SourceState.BEEN_ISSUED_FROZEN.getCode())) {
                        isInvalid = true;
                        sourceMapper.updateStatus(cacheId, SourceState.SOURCE_FROZEN.getCode(),
                                source.getModifyTime());
                    }
                }
            }
            if (isInvalid) {
                agentTaskCache.invalidate(agentIp);
            }

            return true;
        } catch (Throwable t) {
            LOGGER.error("put source snapshot error", t);
            return false;
        }
    }

    @Override
    public void close() {
        this.isClose = true;
    }

    /**
     * The task of saving source task snapshot into DB.
     */
    private class SaveSnapshotTaskRunnable implements Runnable {

        @Override
        public void run() {
            while (!isClose) {
                try {
                    TaskSnapshotRequest request = snapshotQueue.poll(1, TimeUnit.SECONDS);
                    if (request == null || CollectionUtils.isEmpty(request.getSnapshotList())) {
                        continue;
                    }

                    List<TaskSnapshotMessage> requestList = request.getSnapshotList();
                    for (TaskSnapshotMessage message : requestList) {
                        Integer id = message.getJobId();
                        StreamSourceEntity entity = new StreamSourceEntity();
                        entity.setId(id);
                        entity.setSnapshot(message.getSnapshot());
                        entity.setReportTime(request.getReportTime());

                        // update snapshot
                        sourceMapper.updateSnapshot(entity);
                    }
                } catch (Throwable t) {
                    LOGGER.error("source snapshot task runnable error", t);
                }
            }
        }
    }

}
