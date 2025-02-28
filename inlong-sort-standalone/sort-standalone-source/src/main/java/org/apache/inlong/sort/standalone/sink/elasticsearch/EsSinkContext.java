/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.inlong.sort.standalone.sink.elasticsearch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.lang.ClassUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.flume.Channel;
import org.apache.flume.Context;
import org.apache.http.HttpHost;
import org.apache.inlong.sort.standalone.channel.ProfileEvent;
import org.apache.inlong.sort.standalone.config.holder.CommonPropertiesHolder;
import org.apache.inlong.sort.standalone.config.holder.SortClusterConfigHolder;
import org.apache.inlong.sort.standalone.config.pojo.InlongId;
import org.apache.inlong.sort.standalone.config.pojo.SortTaskConfig;
import org.apache.inlong.sort.standalone.metrics.SortMetricItem;
import org.apache.inlong.sort.standalone.metrics.audit.AuditUtils;
import org.apache.inlong.sort.standalone.sink.SinkContext;
import org.apache.inlong.sort.standalone.utils.Constants;
import org.apache.inlong.sort.standalone.utils.InlongLoggerFactory;
import org.slf4j.Logger;

import com.alibaba.fastjson.JSON;

/**
 * 
 * EsSinkContext
 */
public class EsSinkContext extends SinkContext {

    public static final Logger LOG = InlongLoggerFactory.getLogger(EsSinkContext.class);
    public static final String KEY_NODE_ID = "nodeId";
    public static final String KEY_USERNAME = "username";
    public static final String KEY_PASSWORD = "password";
    public static final String KEY_BULK_ACTION = "bulkAction";
    public static final String KEY_BULK_SIZE_MB = "bulkSizeMb";
    public static final String KEY_FLUSH_INTERVAL = "flushInterval";
    public static final String KEY_CONCURRENT_REQUESTS = "concurrentRequests";
    public static final String KEY_MAX_CONNECT = "maxConnect";
    public static final String KEY_KEYWORD_MAX_LENGTH = "keywordMaxLength";
    public static final String KEY_HTTP_HOSTS = "httpHosts";
    public static final String KEY_EVENT_INDEXREQUEST_HANDLER = "indexRequestHandler";
    public static final String KEY_IS_USE_INDEX_ID = "isUseIndexId";

    public static final int DEFAULT_BULK_ACTION = 4000;
    public static final int DEFAULT_BULK_SIZE_MB = 10;
    public static final int DEFAULT_FLUSH_INTERVAL = 60;
    public static final int DEFAULT_CONCURRENT_REQUESTS = 5;
    public static final int DEFAULT_MAX_CONNECT = 10;
    public static final int DEFAULT_KEYWORD_MAX_LENGTH = 32 * 1024 - 1;
    public static final boolean DEFAULT_IS_USE_INDEX_ID = false;

    private Context sinkContext;
    private String nodeId;
    private Map<String, EsIdConfig> idConfigMap = new ConcurrentHashMap<>();
    private final LinkedBlockingQueue<EsIndexRequest> dispatchQueue;
    private AtomicLong offerCounter = new AtomicLong(0);
    private AtomicLong takeCounter = new AtomicLong(0);
    private AtomicLong backCounter = new AtomicLong(0);
    // rest client
    private String username;
    private String password;
    private int bulkAction = DEFAULT_BULK_ACTION;
    private int bulkSizeMb = DEFAULT_BULK_SIZE_MB;
    private int flushInterval = DEFAULT_FLUSH_INTERVAL;
    private int concurrentRequests = DEFAULT_CONCURRENT_REQUESTS;
    private int maxConnect = DEFAULT_MAX_CONNECT;
    private int keywordMaxLength = DEFAULT_KEYWORD_MAX_LENGTH;
    private boolean isUseIndexId = DEFAULT_IS_USE_INDEX_ID;
    // http host
    private String strHttpHosts;
    private HttpHost[] httpHosts;
    // handler
    private IEvent2IndexRequestHandler indexRequestHandler;

    /**
     * Constructor
     * 
     * @param sinkName
     * @param context
     * @param channel
     * @param dispatchQueue
     */
    public EsSinkContext(String sinkName, Context context, Channel channel,
            LinkedBlockingQueue<EsIndexRequest> dispatchQueue) {
        super(sinkName, context, channel);
        this.sinkContext = context;
        this.dispatchQueue = dispatchQueue;
        this.nodeId = CommonPropertiesHolder.getString(KEY_NODE_ID);
    }

    /**
     * reload
     */
    public void reload() {
        try {
            LOG.info("SortTask:{},dispatchQueue:{},offer:{},take:{},back:{}",
                    taskName, dispatchQueue.size(), offerCounter.getAndSet(0),
                    takeCounter.getAndSet(0), backCounter.getAndSet(0));
            SortTaskConfig newSortTaskConfig = SortClusterConfigHolder.getTaskConfig(taskName);
            if (this.sortTaskConfig != null && this.sortTaskConfig.equals(newSortTaskConfig)) {
                return;
            }
            LOG.info("get new SortTaskConfig:taskName:{}:config:{}", taskName,
                    JSON.toJSONString(newSortTaskConfig));
            this.sortTaskConfig = newSortTaskConfig;
            this.sinkContext = new Context(this.sortTaskConfig.getSinkParams());
            // parse the config of id and topic
            Map<String, EsIdConfig> newIdConfigMap = new ConcurrentHashMap<>();
            List<Map<String, String>> idList = this.sortTaskConfig.getIdParams();
            for (Map<String, String> idParam : idList) {
                String inlongGroupId = idParam.get(Constants.INLONG_GROUP_ID);
                String inlongStreamId = idParam.get(Constants.INLONG_STREAM_ID);
                String uid = InlongId.generateUid(inlongGroupId, inlongStreamId);
                String jsonIdConfig = JSON.toJSONString(idParam);
                EsIdConfig idConfig = JSON.parseObject(jsonIdConfig, EsIdConfig.class);
                newIdConfigMap.put(uid, idConfig);
            }
            // change current config
            this.idConfigMap = newIdConfigMap;
            // rest client
            this.username = sinkContext.getString(KEY_USERNAME);
            this.password = sinkContext.getString(KEY_PASSWORD);
            this.bulkAction = sinkContext.getInteger(KEY_BULK_ACTION, DEFAULT_BULK_ACTION);
            this.bulkSizeMb = sinkContext.getInteger(KEY_BULK_SIZE_MB, DEFAULT_BULK_SIZE_MB);
            this.flushInterval = sinkContext.getInteger(KEY_FLUSH_INTERVAL, DEFAULT_FLUSH_INTERVAL);
            this.concurrentRequests = sinkContext.getInteger(KEY_CONCURRENT_REQUESTS, DEFAULT_CONCURRENT_REQUESTS);
            this.maxConnect = sinkContext.getInteger(KEY_MAX_CONNECT, DEFAULT_MAX_CONNECT);
            this.isUseIndexId = sinkContext.getBoolean(KEY_IS_USE_INDEX_ID, DEFAULT_IS_USE_INDEX_ID);
            // http host
            this.strHttpHosts = sinkContext.getString(KEY_HTTP_HOSTS);
            if (!StringUtils.isBlank(strHttpHosts)) {
                String[] strHttpHostArray = strHttpHosts.split("\\s+");
                List<HttpHost> newHttpHosts = new ArrayList<>(strHttpHostArray.length);
                for (String strHttpHost : strHttpHostArray) {
                    String[] ipPort = strHttpHost.split(":");
                    if (ipPort.length == 2 && NumberUtils.isDigits(ipPort[1])) {
                        newHttpHosts.add(new HttpHost(ipPort[0], NumberUtils.toInt(ipPort[1])));
                    }
                }
                if (newHttpHosts.size() > 0) {
                    HttpHost[] newHostHostArray = new HttpHost[newHttpHosts.size()];
                    this.httpHosts = newHttpHosts.toArray(newHostHostArray);
                }
            }
            // IEvent2IndexRequestHandler
            String indexRequestHandlerClass = CommonPropertiesHolder.getString(KEY_EVENT_INDEXREQUEST_HANDLER,
                    DefaultEvent2IndexRequestHandler.class.getName());
            try {
                Class<?> handlerClass = ClassUtils.getClass(indexRequestHandlerClass);
                Object handlerObject = handlerClass.getDeclaredConstructor().newInstance();
                if (handlerObject instanceof IEvent2IndexRequestHandler) {
                    this.indexRequestHandler = (IEvent2IndexRequestHandler) handlerObject;
                }
            } catch (Throwable t) {
                LOG.error("Fail to init IEvent2IndexRequestHandler,handlerClass:{},error:{}",
                        indexRequestHandlerClass, t.getMessage());
            }
            // log
            LOG.info("end to get SortTaskConfig:taskName:{}:newIdConfigMap:{}", taskName,
                    JSON.toJSONString(newIdConfigMap));
        } catch (Throwable e) {
            LOG.error(e.getMessage(), e);
        }
    }

    /**
     * addSendMetric
     * 
     * @param currentRecord
     * @param bid
     */
    public void addSendMetric(ProfileEvent currentRecord, String bid) {
        Map<String, String> dimensions = new HashMap<>();
        dimensions.put(SortMetricItem.KEY_CLUSTER_ID, this.getClusterId());
        dimensions.put(SortMetricItem.KEY_TASK_NAME, this.getTaskName());
        // metric
        fillInlongId(currentRecord, dimensions);
        dimensions.put(SortMetricItem.KEY_SINK_ID, this.getSinkName());
        dimensions.put(SortMetricItem.KEY_SINK_DATA_ID, bid);
        long msgTime = currentRecord.getRawLogTime();
        long auditFormatTime = msgTime - msgTime % CommonPropertiesHolder.getAuditFormatInterval();
        dimensions.put(SortMetricItem.KEY_MESSAGE_TIME, String.valueOf(auditFormatTime));
        SortMetricItem metricItem = this.getMetricItemSet().findMetricItem(dimensions);
        long count = 1;
        long size = currentRecord.getBody().length;
        metricItem.sendCount.addAndGet(count);
        metricItem.sendSize.addAndGet(size);
    }

    /**
     * addReadFailMetric
     */
    public void addSendFailMetric() {
        Map<String, String> dimensions = new HashMap<>();
        dimensions.put(SortMetricItem.KEY_CLUSTER_ID, this.getClusterId());
        dimensions.put(SortMetricItem.KEY_SINK_ID, this.getSinkName());
        long msgTime = System.currentTimeMillis();
        long auditFormatTime = msgTime - msgTime % CommonPropertiesHolder.getAuditFormatInterval();
        dimensions.put(SortMetricItem.KEY_MESSAGE_TIME, String.valueOf(auditFormatTime));
        SortMetricItem metricItem = this.getMetricItemSet().findMetricItem(dimensions);
        metricItem.readFailCount.incrementAndGet();
    }

    /**
     * fillInlongId
     * 
     * @param currentRecord
     * @param dimensions
     */
    public static void fillInlongId(ProfileEvent currentRecord, Map<String, String> dimensions) {
        String inlongGroupId = currentRecord.getInlongGroupId();
        inlongGroupId = (StringUtils.isBlank(inlongGroupId)) ? "-" : inlongGroupId;
        String inlongStreamId = currentRecord.getInlongStreamId();
        inlongStreamId = (StringUtils.isBlank(inlongStreamId)) ? "-" : inlongStreamId;
        dimensions.put(SortMetricItem.KEY_INLONG_GROUP_ID, inlongGroupId);
        dimensions.put(SortMetricItem.KEY_INLONG_STREAM_ID, inlongStreamId);
    }

    /**
     * addSendResultMetric
     * 
     * @param currentRecord
     * @param bid
     * @param result
     * @param sendTime
     */
    public void addSendResultMetric(ProfileEvent currentRecord, String bid, boolean result, long sendTime) {
        Map<String, String> dimensions = new HashMap<>();
        dimensions.put(SortMetricItem.KEY_CLUSTER_ID, this.getClusterId());
        dimensions.put(SortMetricItem.KEY_TASK_NAME, this.getTaskName());
        // metric
        fillInlongId(currentRecord, dimensions);
        dimensions.put(SortMetricItem.KEY_SINK_ID, this.getSinkName());
        dimensions.put(SortMetricItem.KEY_SINK_DATA_ID, bid);
        long msgTime = currentRecord.getRawLogTime();
        long auditFormatTime = msgTime - msgTime % CommonPropertiesHolder.getAuditFormatInterval();
        dimensions.put(SortMetricItem.KEY_MESSAGE_TIME, String.valueOf(auditFormatTime));
        SortMetricItem metricItem = this.getMetricItemSet().findMetricItem(dimensions);
        long count = 1;
        long size = currentRecord.getBody().length;
        if (result) {
            metricItem.sendSuccessCount.addAndGet(count);
            metricItem.sendSuccessSize.addAndGet(size);
            AuditUtils.add(AuditUtils.AUDIT_ID_SEND_SUCCESS, currentRecord);
            if (sendTime > 0) {
                long currentTime = System.currentTimeMillis();
                long sinkDuration = currentTime - sendTime;
                long nodeDuration = currentTime - NumberUtils.toLong(Constants.HEADER_KEY_SOURCE_TIME, msgTime);
                long wholeDuration = currentTime - msgTime;
                metricItem.sinkDuration.addAndGet(sinkDuration * count);
                metricItem.nodeDuration.addAndGet(nodeDuration * count);
                metricItem.wholeDuration.addAndGet(wholeDuration * count);
            }
        } else {
            metricItem.sendFailCount.addAndGet(count);
            metricItem.sendFailSize.addAndGet(size);
        }
    }

    /**
     * getIdConfig
     * 
     * @param  uid
     * @return
     */
    public EsIdConfig getIdConfig(String uid) {
        return this.idConfigMap.get(uid);
    }

    /**
     * get nodeId
     * 
     * @return the nodeId
     */
    public String getNodeId() {
        return nodeId;
    }

    /**
     * get idConfigMap
     * 
     * @return the idConfigMap
     */
    public Map<String, EsIdConfig> getIdConfigMap() {
        return idConfigMap;
    }

    /**
     * get sinkContext
     * 
     * @return the sinkContext
     */
    public Context getSinkContext() {
        return sinkContext;
    }

    /**
     * set sinkContext
     * 
     * @param sinkContext the sinkContext to set
     */
    public void setSinkContext(Context sinkContext) {
        this.sinkContext = sinkContext;
    }

    /**
     * offerDispatchQueue
     * 
     * @param  indexRequest
     * @return
     */
    public boolean offerDispatchQueue(EsIndexRequest indexRequest) {
        this.offerCounter.incrementAndGet();
        return dispatchQueue.offer(indexRequest);
    }

    /**
     * taskDispatchQueue
     * 
     * @return
     */
    public EsIndexRequest taskDispatchQueue() {
        EsIndexRequest indexRequest = this.dispatchQueue.poll();
        if (indexRequest != null) {
            this.takeCounter.incrementAndGet();
        }
        return indexRequest;
    }

    /**
     * backDispatchQueue
     * 
     * @param  indexRequest
     * @return
     */
    public boolean backDispatchQueue(EsIndexRequest indexRequest) {
        this.backCounter.incrementAndGet();
        return dispatchQueue.offer(indexRequest);
    }

    /**
     * get bulkAction
     * 
     * @return the bulkAction
     */
    public int getBulkAction() {
        return bulkAction;
    }

    /**
     * set bulkAction
     * 
     * @param bulkAction the bulkAction to set
     */
    public void setBulkAction(int bulkAction) {
        this.bulkAction = bulkAction;
    }

    /**
     * get bulkSizeMb
     * 
     * @return the bulkSizeMb
     */
    public int getBulkSizeMb() {
        return bulkSizeMb;
    }

    /**
     * set bulkSizeMb
     * 
     * @param bulkSizeMb the bulkSizeMb to set
     */
    public void setBulkSizeMb(int bulkSizeMb) {
        this.bulkSizeMb = bulkSizeMb;
    }

    /**
     * get flushInterval
     * 
     * @return the flushInterval
     */
    public int getFlushInterval() {
        return flushInterval;
    }

    /**
     * set flushInterval
     * 
     * @param flushInterval the flushInterval to set
     */
    public void setFlushInterval(int flushInterval) {
        this.flushInterval = flushInterval;
    }

    /**
     * get concurrentRequests
     * 
     * @return the concurrentRequests
     */
    public int getConcurrentRequests() {
        return concurrentRequests;
    }

    /**
     * set concurrentRequests
     * 
     * @param concurrentRequests the concurrentRequests to set
     */
    public void setConcurrentRequests(int concurrentRequests) {
        this.concurrentRequests = concurrentRequests;
    }

    /**
     * get maxConnect
     * 
     * @return the maxConnect
     */
    public int getMaxConnect() {
        return maxConnect;
    }

    /**
     * set maxConnect
     * 
     * @param maxConnect the maxConnect to set
     */
    public void setMaxConnect(int maxConnect) {
        this.maxConnect = maxConnect;
    }

    /**
     * get strHttpHosts
     * 
     * @return the strHttpHosts
     */
    public String getStrHttpHosts() {
        return strHttpHosts;
    }

    /**
     * set strHttpHosts
     * 
     * @param strHttpHosts the strHttpHosts to set
     */
    public void setStrHttpHosts(String strHttpHosts) {
        this.strHttpHosts = strHttpHosts;
    }

    /**
     * get httpHosts
     * 
     * @return the httpHosts
     */
    public HttpHost[] getHttpHosts() {
        return httpHosts;
    }

    /**
     * set httpHosts
     * 
     * @param httpHosts the httpHosts to set
     */
    public void setHttpHosts(HttpHost[] httpHosts) {
        this.httpHosts = httpHosts;
    }

    /**
     * set nodeId
     * 
     * @param nodeId the nodeId to set
     */
    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    /**
     * set idConfigMap
     * 
     * @param idConfigMap the idConfigMap to set
     */
    public void setIdConfigMap(Map<String, EsIdConfig> idConfigMap) {
        this.idConfigMap = idConfigMap;
    }

    /**
     * get username
     * 
     * @return the username
     */
    public String getUsername() {
        return username;
    }

    /**
     * set username
     * 
     * @param username the username to set
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * get password
     * 
     * @return the password
     */
    public String getPassword() {
        return password;
    }

    /**
     * set password
     * 
     * @param password the password to set
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * get keywordMaxLength
     * 
     * @return the keywordMaxLength
     */
    public int getKeywordMaxLength() {
        return keywordMaxLength;
    }

    /**
     * set keywordMaxLength
     * 
     * @param keywordMaxLength the keywordMaxLength to set
     */
    public void setKeywordMaxLength(int keywordMaxLength) {
        this.keywordMaxLength = keywordMaxLength;
    }

    /**
     * get isUseIndexId
     * 
     * @return the isUseIndexId
     */
    public boolean isUseIndexId() {
        return isUseIndexId;
    }

    /**
     * set isUseIndexId
     * 
     * @param isUseIndexId the isUseIndexId to set
     */
    public void setUseIndexId(boolean isUseIndexId) {
        this.isUseIndexId = isUseIndexId;
    }

    /**
     * get indexRequestHandler
     * 
     * @return the indexRequestHandler
     */
    public IEvent2IndexRequestHandler getIndexRequestHandler() {
        return indexRequestHandler;
    }

    /**
     * set indexRequestHandler
     * 
     * @param indexRequestHandler the indexRequestHandler to set
     */
    public void setIndexRequestHandler(IEvent2IndexRequestHandler indexRequestHandler) {
        this.indexRequestHandler = indexRequestHandler;
    }

}
