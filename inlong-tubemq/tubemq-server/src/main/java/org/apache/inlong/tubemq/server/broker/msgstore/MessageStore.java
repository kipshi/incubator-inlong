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

package org.apache.inlong.tubemq.server.broker.msgstore;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.apache.inlong.tubemq.corebase.TBaseConstants;
import org.apache.inlong.tubemq.corebase.TErrCodeConstants;
import org.apache.inlong.tubemq.corebase.protobuf.generated.ClientBroker;
import org.apache.inlong.tubemq.corebase.utils.MixedUtils;
import org.apache.inlong.tubemq.corebase.utils.ThreadUtils;
import org.apache.inlong.tubemq.server.broker.BrokerConfig;
import org.apache.inlong.tubemq.server.broker.metadata.TopicMetadata;
import org.apache.inlong.tubemq.server.broker.msgstore.disk.GetMessageResult;
import org.apache.inlong.tubemq.server.broker.msgstore.disk.MsgFileStatisInfo;
import org.apache.inlong.tubemq.server.broker.msgstore.disk.MsgFileStore;
import org.apache.inlong.tubemq.server.broker.msgstore.disk.Segment;
import org.apache.inlong.tubemq.server.broker.msgstore.mem.GetCacheMsgResult;
import org.apache.inlong.tubemq.server.broker.msgstore.mem.MsgMemStatisInfo;
import org.apache.inlong.tubemq.server.broker.msgstore.mem.MsgMemStore;
import org.apache.inlong.tubemq.server.broker.nodeinfo.ConsumerNodeInfo;
import org.apache.inlong.tubemq.server.broker.stats.TrafficInfo;
import org.apache.inlong.tubemq.server.broker.utils.DataStoreUtils;
import org.apache.inlong.tubemq.server.common.utils.AppendResult;
import org.apache.inlong.tubemq.server.common.utils.IdWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Topic's message storage. It's a logical topic storage. Contains multi types storage: data in memory,
 * data in disk, and statistics of produce and consume.
 */
public class MessageStore implements Closeable {
    private static final Logger logger = LoggerFactory.getLogger(MessageStore.class);
    private final ReentrantLock flushMutex = new ReentrantLock();
    private final AtomicBoolean hasFlushBeenTriggered = new AtomicBoolean(false);
    private final TopicMetadata topicMetadata;
    // sequencer id generator.
    private final IdWorker idWorker;
    private final int storeId;
    private final String storeKey;
    private final BrokerConfig tubeConfig;
    private final String primStorePath;
    private final AtomicLong lastMemFlushTime = new AtomicLong(0);
    private final MessageStoreManager msgStoreMgr;
    private final MsgMemStatisInfo msgMemStatisInfo = new MsgMemStatisInfo();
    private final MsgFileStatisInfo msgFileStatisInfo = new MsgFileStatisInfo();
    private final MsgFileStore msgFileStore;
    private final ReentrantReadWriteLock writeCacheMutex = new ReentrantReadWriteLock();
    private final Condition flushWriteCacheCondition = writeCacheMutex.writeLock().newCondition();
    private final AtomicBoolean isFlushOngoing = new AtomicBoolean(false);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private volatile int partitionNum;
    private final AtomicInteger unflushInterval = new AtomicInteger(0);
    private final AtomicInteger unflushThreshold = new AtomicInteger(0);
    private final AtomicInteger unflushDataHold = new AtomicInteger(0);
    private volatile int writeCacheMaxSize;
    private volatile int writeCacheMaxCnt;
    private volatile int writeCacheFlushIntvl;
    private final AtomicLong maxFileValidDurMs = new AtomicLong(0);
    private int maxAllowRdSize = 262144;
    private final AtomicInteger memMaxIndexReadCnt = new AtomicInteger(6000);
    private final AtomicInteger fileMaxIndexReadCnt = new AtomicInteger(8000);
    private final AtomicInteger memMaxFilterIndexReadCnt
            = new AtomicInteger(memMaxIndexReadCnt.get() * 2);
    private final AtomicInteger fileMaxFilterIndexReadCnt
            = new AtomicInteger(fileMaxIndexReadCnt.get() * 3);
    private final AtomicInteger fileLowReqMaxFilterIndexReadCnt
            = new AtomicInteger(fileMaxIndexReadCnt.get() * 10);
    private final AtomicInteger fileMaxIndexReadSize
            = new AtomicInteger(this.fileMaxIndexReadCnt.get() * DataStoreUtils.STORE_INDEX_HEAD_LEN);
    private final AtomicInteger fileMaxFilterIndexReadSize
            = new AtomicInteger(this.fileMaxFilterIndexReadCnt.get() * DataStoreUtils.STORE_INDEX_HEAD_LEN);
    private final AtomicInteger fileLowReqMaxFilterIndexReadSize
            = new AtomicInteger(this.fileLowReqMaxFilterIndexReadCnt.get() * DataStoreUtils.STORE_INDEX_HEAD_LEN);
    private MsgMemStore msgMemStore;
    private MsgMemStore msgMemStoreBeingFlush;

    public MessageStore(final MessageStoreManager messageStoreManager,
                        final TopicMetadata topicMetadata, final int storeId,
                        final BrokerConfig tubeConfig,
                        final int maxMsgRDSize) throws IOException {
        this(messageStoreManager, topicMetadata, storeId, tubeConfig, 0, maxMsgRDSize);
    }

    public MessageStore(final MessageStoreManager messageStoreManager,
                        final TopicMetadata topicMetadata, final int storeId,
                        final BrokerConfig tubeConfig, final long offsetIfCreate,
                        final int maxMsgRDSize) throws IOException {
        this.topicMetadata = topicMetadata;
        this.storeId = storeId;
        this.tubeConfig = tubeConfig;
        this.msgStoreMgr = messageStoreManager;
        this.maxAllowRdSize = (int) (maxMsgRDSize * 0.5);
        this.storeKey = topicMetadata.getTopic() + "-" + this.storeId;
        this.idWorker = new IdWorker(0);
        this.primStorePath = this.tubeConfig.getPrimaryPath();
        this.partitionNum = topicMetadata.getNumPartitions();
        this.unflushInterval.set(topicMetadata.getUnflushInterval());
        this.maxFileValidDurMs.set(parseDeletePolicy(topicMetadata.getDeletePolicy()));
        this.unflushThreshold.set(topicMetadata.getUnflushThreshold());
        this.unflushDataHold.set(topicMetadata.getUnflushDataHold());
        this.writeCacheMaxCnt = topicMetadata.getMemCacheMsgCnt();
        this.writeCacheMaxSize = validAndGetMemCacheSize(topicMetadata);
        this.writeCacheFlushIntvl = topicMetadata.getMemCacheFlushIntvl();
        int tmpIndexReadCnt = tubeConfig.getIndexTransCount() * partitionNum;
        memMaxIndexReadCnt.set(MixedUtils.mid(tmpIndexReadCnt, 6000, 10000));
        fileMaxIndexReadCnt.set(MixedUtils.mid(tmpIndexReadCnt, 8000, 13500));
        memMaxFilterIndexReadCnt.set(memMaxIndexReadCnt.get() * 2);
        fileMaxFilterIndexReadCnt.set(fileMaxIndexReadCnt.get() * 3);
        fileLowReqMaxFilterIndexReadCnt.set(fileMaxFilterIndexReadCnt.get() * 10);
        fileMaxIndexReadSize.set(this.fileMaxIndexReadCnt.get() * DataStoreUtils.STORE_INDEX_HEAD_LEN);
        fileMaxFilterIndexReadSize.set(this.fileMaxFilterIndexReadCnt.get() * DataStoreUtils.STORE_INDEX_HEAD_LEN);
        fileLowReqMaxFilterIndexReadSize.set(
                this.fileLowReqMaxFilterIndexReadCnt.get() * DataStoreUtils.STORE_INDEX_HEAD_LEN);
        this.msgFileStore = new MsgFileStore(this, this.tubeConfig, this.primStorePath, offsetIfCreate);
        this.msgMemStore = new MsgMemStore(this.writeCacheMaxSize, this.writeCacheMaxCnt, this.tubeConfig);
        this.msgMemStore.resetStartPos(this.msgFileStore.getDataMaxOffset(), this.msgFileStore.getIndexMaxOffset());
        this.msgMemStoreBeingFlush = new MsgMemStore(this.writeCacheMaxSize, this.writeCacheMaxCnt, this.tubeConfig);
        this.msgMemStoreBeingFlush.resetStartPos(
                this.msgFileStore.getDataMaxOffset(), this.msgFileStore.getIndexMaxOffset());
        this.lastMemFlushTime.set(System.currentTimeMillis());
    }

    /**
     * Get message from message store. Support the given offset, filter.
     *
     * @param reqSwitch            read message from where
     * @param requestOffset        the request offset to read
     * @param partitionId          the partitionId for reading messages
     * @param consumerNodeInfo     the consumer object
     * @param statisKeyBase        the statistical key prefix
     * @param msgSizeLimit         the max read size
     * @param reqRcvTime           the timestamp of the record to be checked
     * @return                     read result
     * @throws IOException         the exception during processing
     */
    public GetMessageResult getMessages(int reqSwitch, long requestOffset,
                                        int partitionId, ConsumerNodeInfo consumerNodeInfo,
                                        String statisKeyBase, int msgSizeLimit,
                                        long reqRcvTime) throws IOException {
        // #lizard forgives
        if (this.closed.get()) {
            throw new IllegalStateException(new StringBuilder(512)
                    .append("[Data Store] Closed MessageStore for storeKey ")
                    .append(this.storeKey).toString());
        }
        int result = 0;
        boolean inMemCache = false;
        int maxIndexReadLength = memMaxIndexReadCnt.get();
        GetCacheMsgResult memMsgRlt = new GetCacheMsgResult(false, TErrCodeConstants.NOT_FOUND,
                requestOffset, "Can't found Message by index in cache");
        // determine position to read.
        reqSwitch = (reqSwitch <= 0)
                ? 0 : (consumerNodeInfo.isFilterConsume() ? (reqSwitch % 100) : (reqSwitch / 100));
        if (reqSwitch > 1) {
            // in read memory situation, read main memory or backup memory by consumer's config.
            long maxIndexOffset = TBaseConstants.META_VALUE_UNDEFINED;
            if (requestOffset >= this.msgFileStore.getIndexMaxOffset()) {
                this.writeCacheMutex.readLock().lock();
                try {
                    maxIndexOffset = this.msgMemStore.getIndexLastWritePos();
                    result = this.msgMemStoreBeingFlush.isOffsetInHold(requestOffset);
                    if (result >= 0) {
                        inMemCache = true;
                        if (result > 0) {
                            if (reqSwitch > 2) {
                                memMsgRlt =
                                        // read from main memory.
                                        msgMemStore.getMessages(consumerNodeInfo.getLastDataRdOffset(),
                                                requestOffset, msgStoreMgr.getMaxMsgTransferSize(),
                                                maxIndexReadLength, partitionId, false,
                                                consumerNodeInfo.isFilterConsume(),
                                                consumerNodeInfo.getFilterCondCodeSet(), reqRcvTime);
                            }
                        } else {
                            // read from backup memory.
                            memMsgRlt =
                                    msgMemStoreBeingFlush.getMessages(consumerNodeInfo.getLastDataRdOffset(),
                                            requestOffset, msgStoreMgr.getMaxMsgTransferSize(),
                                            maxIndexReadLength, partitionId, true,
                                            consumerNodeInfo.isFilterConsume(),
                                            consumerNodeInfo.getFilterCondCodeSet(), reqRcvTime);
                        }
                    }
                } finally {
                    this.writeCacheMutex.readLock().unlock();
                }
            }
            if (inMemCache) {
                // return not found when data is under memory sink operation.
                if (memMsgRlt.isSuccess) {
                    HashMap<String, TrafficInfo> countMap =
                            new HashMap<>();
                    List<ClientBroker.TransferedMessage> transferedMessageList =
                            new ArrayList<>();
                    if (!memMsgRlt.cacheMsgList.isEmpty()) {
                        final StringBuilder strBuffer = new StringBuilder(512);
                        for (ByteBuffer dataBuffer : memMsgRlt.cacheMsgList) {
                            ClientBroker.TransferedMessage transferedMessage =
                                    DataStoreUtils.getTransferMsg(dataBuffer,
                                            dataBuffer.array().length,
                                            countMap, statisKeyBase, strBuffer);
                            if (transferedMessage != null) {
                                transferedMessageList.add(transferedMessage);
                            }
                        }
                    }
                    GetMessageResult getResult =
                        new GetMessageResult(true, 0, memMsgRlt.errInfo, requestOffset,
                            memMsgRlt.dltOffset, memMsgRlt.lastRdDataOff,
                            memMsgRlt.totalMsgSize, countMap, transferedMessageList);
                    getResult.setMaxOffset(maxIndexOffset);
                    return getResult;
                } else {
                    return new GetMessageResult(false, memMsgRlt.retCode, requestOffset,
                            memMsgRlt.dltOffset, memMsgRlt.errInfo);
                }
            }
        }
        // before read from file, adjust request's offset.
        long reqNewOffset = Math.max(requestOffset, this.msgFileStore.getIndexMinOffset());
        if (reqSwitch <= 1 && reqNewOffset >= getFileIndexMaxOffset()) {
            return new GetMessageResult(false, TErrCodeConstants.NOT_FOUND,
                    reqNewOffset, 0, "current offset is exceed max file offset");
        }
        maxIndexReadLength = consumerNodeInfo.isFilterConsume()
                ? fileMaxFilterIndexReadSize.get() : fileMaxIndexReadSize.get();
        final ByteBuffer indexBuffer = ByteBuffer.allocate(maxIndexReadLength);
        Segment indexRecordView =
                this.msgFileStore.indexSlice(reqNewOffset, maxIndexReadLength);
        if (indexRecordView == null) {
            if (reqNewOffset < this.msgFileStore.getIndexMinOffset()) {
                return new GetMessageResult(false, TErrCodeConstants.MOVED,
                        reqNewOffset, 0, "current offset is exceed min offset!");
            } else {
                return new GetMessageResult(false, TErrCodeConstants.NOT_FOUND,
                        reqNewOffset, 0, "current offset is exceed max offset!");
            }
        }
        indexRecordView.read(indexBuffer, reqNewOffset);
        indexBuffer.flip();
        indexRecordView.relViewRef();
        if ((msgFileStore.getDataHighMaxOffset() - consumerNodeInfo.getLastDataRdOffset()
            >= this.tubeConfig.getDoubleDefaultDeduceReadSize())
            && msgSizeLimit > this.maxAllowRdSize) {
            msgSizeLimit = this.maxAllowRdSize;
        }
        GetMessageResult retResult =
            msgFileStore.getMessages(partitionId,
                consumerNodeInfo.getLastDataRdOffset(), reqNewOffset,
                indexBuffer, consumerNodeInfo.isFilterConsume(),
                consumerNodeInfo.getFilterCondCodeSet(),
                statisKeyBase, msgSizeLimit, reqRcvTime);
        if (reqSwitch <= 1) {
            retResult.setMaxOffset(getFileIndexMaxOffset());
        } else {
            retResult.setMaxOffset(getIndexMaxOffset());
        }
        if (consumerNodeInfo.isFilterConsume()
            && retResult.isSuccess
            && retResult.getLastReadOffset() > 0) {
            if ((getFileIndexMaxOffset()
                - reqNewOffset - retResult.getLastReadOffset())
                < fileLowReqMaxFilterIndexReadSize.get()) {
                retResult.setSlowFreq(true);
            }
        }
        return retResult;
    }

    /**
     * Get start offset by timestamp.
     *
     * @param timestamp  timestamp
     * @return start offset
     */
    public long getStartOffsetByTimeStamp(long timestamp) {
        if (this.closed.get()) {
            throw new IllegalStateException(new StringBuilder(512)
                    .append("[Data Store] Closed MessageStore for storeKey ")
                    .append(this.storeKey).toString());
        }
        if (timestamp <= this.msgFileStore.getIndexMaxAppendTime()) {
            return this.msgFileStore.getStartOffsetByTimeStamp(timestamp);
        }
        this.writeCacheMutex.readLock().lock();
        try {
            // read from backup memory.
            if (timestamp <= this.msgMemStoreBeingFlush.getRightAppendTime()) {
                return this.msgMemStoreBeingFlush.getIndexStartWritePos();
            }
            // read from main memory.
            return this.msgMemStore.getIndexStartWritePos();
        } finally {
            this.writeCacheMutex.readLock().unlock();
        }
    }

    /**
     * Append msg to store.
     *
     * @param appendResult    the append result
     * @param dataLength      the data length
     * @param dataCheckSum    the check sum of message data
     * @param data            the message data
     * @param msgTypeCode     the filter item hash code
     * @param msgFlag         the message flag
     * @param partitionId     the partitionId for append messages
     * @param sentAddr        the address to send the message to
     *
     * @return                the process result
     * @throws IOException    the exception during processing
     */
    public boolean appendMsg(AppendResult appendResult, int dataLength,
                             int dataCheckSum, byte[] data,
                             int msgTypeCode, int msgFlag,
                             int partitionId, int sentAddr) throws IOException {
        return appendMsg2(appendResult, dataLength, dataCheckSum, data,
                msgTypeCode, msgFlag, partitionId, sentAddr,
                System.currentTimeMillis(), 3, 2);
    }

    /**
     * Append msg to store.
     *
     * @param appendResult    the append result
     * @param dataLength      the data length
     * @param dataCheckSum    the check sum of message data
     * @param data            the message data
     * @param msgTypeCode     the filter item hash code
     * @param msgFlag         the message flag
     * @param partitionId     the partitionId for append messages
     * @param sentAddr        the address to send the message to
     * @param receivedTime    the received time of message
     * @param count           the retry count while full
     * @param waitRetryMs     the wait duration while retry
     *
     * @return                the process result
     * @throws IOException    the exception during processing
     */
    public boolean appendMsg2(AppendResult appendResult, int dataLength,
                              int dataCheckSum, byte[] data,
                              int msgTypeCode, int msgFlag,
                              int partitionId, int sentAddr,
                              long receivedTime, int count,
                              long waitRetryMs) throws IOException {
        if (this.closed.get()) {
            throw new IllegalStateException(new StringBuilder(512)
                    .append("[Data Store] Closed MessageStore for storeKey ")
                    .append(this.storeKey).toString());
        }
        long messageId = this.idWorker.nextId();
        int msgBufLen = DataStoreUtils.STORE_DATA_HEADER_LEN + dataLength;
        final ByteBuffer buffer = ByteBuffer.allocate(msgBufLen);
        buffer.putInt(DataStoreUtils.STORE_DATA_PREFX_LEN + dataLength);
        buffer.putInt(DataStoreUtils.STORE_DATA_TOKER_BEGIN_VALUE);
        buffer.putInt(dataCheckSum);
        buffer.putInt(partitionId);
        buffer.putLong(-1L);
        buffer.putLong(receivedTime);
        buffer.putInt(sentAddr);
        buffer.putInt(msgTypeCode);
        buffer.putLong(messageId);
        buffer.putInt(msgFlag);
        buffer.put(data);
        buffer.flip();
        appendResult.putReceivedInfo(messageId, receivedTime);
        do {
            this.writeCacheMutex.readLock().lock();
            try {
                if (this.msgMemStore.appendMsg(msgMemStatisInfo,
                        partitionId, msgTypeCode, receivedTime,
                        msgBufLen, buffer, appendResult)) {
                    return true;
                }
            } finally {
                this.writeCacheMutex.readLock().unlock();
            }
            if (triggerFlushAndAddMsg(partitionId, msgTypeCode,
                    receivedTime, msgBufLen, true,
                    buffer, false, appendResult)) {
                return true;
            }
            ThreadUtils.sleep(waitRetryMs);
        } while (count-- >= 0);
        msgMemStatisInfo.addWriteFailCount();
        return false;
    }

    public String getCurMemMsgSizeStatisInfo(boolean needRefresh) {
        return msgMemStatisInfo.getCurMsgSizeStatisInfo(needRefresh);
    }

    public String getCurFileMsgSizeStatisInfo(boolean needRefresh) {
        return msgFileStatisInfo.getCurMsgSizeStatisInfo(needRefresh);
    }

    public MsgFileStatisInfo getFileMsgSizeStatisInfo() {
        return this.msgFileStatisInfo;
    }

    /**
     * Execute cleanup policy.
     *
     * @param onlyCheck   whether only check status
     *
     * @return whether clear up segments
     */
    public boolean runClearupPolicy(boolean onlyCheck) {
        if (this.closed.get()) {
            throw new IllegalStateException(new StringBuilder(512)
                    .append("[Data Store] Closed MessageStore for storeKey ")
                    .append(this.storeKey).toString());
        }
        return msgFileStore.runClearupPolicy(onlyCheck);
    }

    /**
     * Refresh unflush threshold
     *
     * @param topicMetadata   topic meta data
     */
    public void refreshUnflushThreshold(TopicMetadata topicMetadata) {
        if (this.closed.get()) {
            throw new IllegalStateException(new StringBuilder(512)
                    .append("[Data Store] Closed MessageStore for storeKey ")
                    .append(this.storeKey).toString());
        }
        partitionNum = topicMetadata.getNumPartitions();
        unflushInterval.set(topicMetadata.getUnflushInterval());
        unflushThreshold.set(topicMetadata.getUnflushThreshold());
        unflushDataHold.set(topicMetadata.getUnflushDataHold());
        maxFileValidDurMs.set(parseDeletePolicy(topicMetadata.getDeletePolicy()));
        int tmpIndexReadCnt = tubeConfig.getIndexTransCount() * partitionNum;
        memMaxIndexReadCnt.set(MixedUtils.mid(tmpIndexReadCnt, 6000, 10000));
        fileMaxIndexReadCnt.set(MixedUtils.mid(tmpIndexReadCnt, 8000, 13500));
        memMaxFilterIndexReadCnt.set(memMaxIndexReadCnt.get() * 2);
        fileMaxFilterIndexReadCnt.set(fileMaxIndexReadCnt.get() * 3);
        fileLowReqMaxFilterIndexReadCnt.set(fileMaxFilterIndexReadCnt.get() * 10);
        fileMaxIndexReadSize.set(fileMaxIndexReadCnt.get() * DataStoreUtils.STORE_INDEX_HEAD_LEN);
        fileMaxFilterIndexReadSize.set(fileMaxFilterIndexReadCnt.get() * DataStoreUtils.STORE_INDEX_HEAD_LEN);
        fileLowReqMaxFilterIndexReadSize.set(
                fileLowReqMaxFilterIndexReadCnt.get() * DataStoreUtils.STORE_INDEX_HEAD_LEN);
        writeCacheMutex.readLock().lock();
        try {
            writeCacheMaxCnt = topicMetadata.getMemCacheMsgCnt();
            writeCacheMaxSize = validAndGetMemCacheSize(topicMetadata);
            writeCacheFlushIntvl = topicMetadata.getMemCacheFlushIntvl();
        } finally {
            writeCacheMutex.readLock().unlock();
        }
    }

    /**
     * Flush file store to disk.
     *
     * @throws IOException the exception during processing
     */
    public void flushFile() throws IOException {
        if (this.closed.get()) {
            throw new IllegalStateException(new StringBuilder(512)
                    .append("[Data Store] Closed MessageStore for storeKey ")
                    .append(this.storeKey).toString());
        }
        msgFileStore.flushDiskFile();
    }

    /**
     * Flush memory store to file.
     *
     * @throws IOException the exception during processing
     */
    public void flushMemCacheData() throws IOException {
        if (this.closed.get()) {
            throw new IllegalStateException(new StringBuilder(512)
                    .append("[Data Store] Closed MessageStore for storeKey ")
                    .append(this.storeKey).toString());
        }
        if (msgMemStore.getCurMsgCount() > 0
                && (System.currentTimeMillis() - this.lastMemFlushTime.get()) >= this.writeCacheFlushIntvl) {

            triggerFlushAndAddMsg(-1, 0, 0, 0, false, null, true, null);
        }
    }

    @Override
    public void close() throws IOException {
        if (this.closed.compareAndSet(false, true)) {
            StringBuilder strBuffer = new StringBuilder(512);
            logger.info(strBuffer.append("[Data Store] Stop current Message store ")
                    .append(this.storeKey).toString());
            strBuffer.delete(0, strBuffer.length());
            ThreadUtils.sleep(100);
            flush(strBuffer);
            this.msgMemStore.close();
            this.msgMemStoreBeingFlush.close();
            this.executor.shutdown();
            this.msgFileStore.close();
            logger.info(strBuffer.append("[Data Store] Message store stopped")
                    .append(this.storeKey).toString());
        }
    }

    public String getTopic() {
        return this.topicMetadata.getTopic();
    }

    public int getStoreId() {
        return this.storeId;
    }

    public String getStoreKey() {
        return this.storeKey;
    }

    public int getPartitionNum() {
        return this.partitionNum;
    }

    public String getPrimStorePath() {
        return this.primStorePath;
    }

    public int getUnflushInterval() {
        return this.unflushInterval.get();
    }

    public long getMaxFileValidDurMs() {
        return maxFileValidDurMs.get();
    }

    public int getUnflushThreshold() {
        return this.unflushThreshold.get();
    }

    public int getUnflushDataHold() {
        return this.unflushDataHold.get();
    }

    public long getFileIndexMaxOffset() {
        return this.msgFileStore.getIndexMaxHighOffset();
    }

    public long getIndexMaxOffset() {
        long lastOffset = 0L;
        this.writeCacheMutex.readLock().lock();
        try {
            lastOffset = this.msgMemStore.getIndexLastWritePos();
        } finally {
            this.writeCacheMutex.readLock().unlock();
        }
        return lastOffset;
    }

    public long getIndexMinOffset() {
        return this.msgFileStore.getIndexMinOffset();
    }

    public long getDataMinOffset() {
        return this.msgFileStore.getDataMinOffset();
    }

    public long getDataMaxOffset() {
        long lastOffset = 0L;
        this.writeCacheMutex.readLock().lock();
        try {
            lastOffset = this.msgMemStore.getDataLastWritePos();
        } finally {
            this.writeCacheMutex.readLock().unlock();
        }
        return lastOffset;
    }

    public long getIndexStoreSize() {
        long totalSize = 0L;
        this.writeCacheMutex.readLock().lock();
        try {
            if (this.msgMemStore.getCurMsgCount() > 0) {
                totalSize += this.msgMemStore.getIndexCacheSize();
            }
            if (this.msgMemStoreBeingFlush.getCurMsgCount() > 0) {
                totalSize += this.msgMemStoreBeingFlush.getIndexCacheSize();
            }
        } finally {
            this.writeCacheMutex.readLock().unlock();
        }
        totalSize += this.msgFileStore.getIndexSizeInBytes();
        return totalSize;
    }

    public long getDataStoreSize() {
        long totalSize = 0L;
        this.writeCacheMutex.readLock().lock();
        try {
            if (this.msgMemStore.getCurMsgCount() > 0) {
                totalSize += this.msgMemStore.getCurDataCacheSize();
            }
            if (this.msgMemStoreBeingFlush.getCurMsgCount() > 0) {
                totalSize += this.msgMemStoreBeingFlush.getCurDataCacheSize();
            }
        } finally {
            this.writeCacheMutex.readLock().unlock();
        }
        totalSize += this.msgFileStore.getDataSizeInBytes();
        return totalSize;
    }

    private long parseDeletePolicy(String delPolicy) {
        String[] tmpStrs = delPolicy.split(",");
        if (tmpStrs.length != 2) {
            return DataStoreUtils.MAX_FILE_VALID_DURATION;
        }
        String validValStr = tmpStrs[1];
        try {
            if (validValStr.endsWith("m")) {
                return Long.parseLong(validValStr.substring(0, validValStr.length() - 1)) * 60000;
            } else if (validValStr.endsWith("s")) {
                return Long.parseLong(validValStr.substring(0, validValStr.length() - 1)) * 1000;
            } else if (validValStr.endsWith("h")) {
                return Long.parseLong(validValStr.substring(0, validValStr.length() - 1)) * 3600000;
            } else {
                return Long.parseLong(validValStr) * 3600000;
            }
        } catch (Throwable e) {
            return DataStoreUtils.MAX_FILE_VALID_DURATION;
        }
    }

    private int validAndGetMemCacheSize(TopicMetadata topicMetadata) {
        int memCacheSize = topicMetadata.getMemCacheMsgSize();
        if (memCacheSize < topicMetadata.getMinMemCacheSize()) {
            logger.info(new StringBuilder(512)
                    .append("[Data Store] ").append(getTopic())
                    .append(" writeCacheMaxSize changed, from ")
                    .append(memCacheSize).append(" to ")
                    .append(topicMetadata.getMinMemCacheSize()).toString());
            memCacheSize = topicMetadata.getMinMemCacheSize();
        }
        return memCacheSize;
    }

    /**
     * Append message and trigger flush operation.
     *
     * @param partitionId       the partitionId for reading messages
     * @param keyCode           the filter item hash code
     * @param receivedTime      the received time of message
     * @param entryLength       the stored entry length
     * @param needAdd           whether to add a message
     * @param entry             the stored entry
     * @param isTimeTrigger     whether is timer trigger
     * @param appendResult      the append result
     *
     * @return                  the append result
     * @throws IOException      the exception during processing
     */
    private boolean triggerFlushAndAddMsg(int partitionId, int keyCode,
                                          long receivedTime, int entryLength,
                                          boolean needAdd, ByteBuffer entry,
                                          boolean isTimeTrigger,
                                          AppendResult appendResult) throws IOException {
        writeCacheMutex.writeLock().lock();
        try {
            if (!isFlushOngoing.get() && hasFlushBeenTriggered.compareAndSet(false, true)) {
                this.executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            final StringBuilder strBuffer = new StringBuilder(512);
                            flush(strBuffer);
                        } catch (Throwable e) {
                            logger.error("[Data Store] Error during flush", e);
                        }
                    }
                });
                msgMemStatisInfo.addMemFlushCount(isTimeTrigger);
            }
            long startTime = System.currentTimeMillis();
            long timeoutNs = TimeUnit.MILLISECONDS.toNanos(100);
            while (hasFlushBeenTriggered.get()) {
                flushWriteCacheCondition.awaitNanos(timeoutNs);
                if (System.currentTimeMillis() - startTime > 2000) {
                    logger.warn(new StringBuilder(512)
                            .append("[Data Store] StoreKey=").append(storeKey)
                            .append(" Wait Cache flush write too long! wait time is ")
                            .append(System.currentTimeMillis() - startTime).toString());
                    break;
                }
            }
            if (needAdd) {
                return msgMemStore.appendMsg(msgMemStatisInfo,
                        partitionId, keyCode, receivedTime,
                        entryLength, entry, appendResult);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(new StringBuilder(512)
                    .append("[Data Store] StoreKey=").append(storeKey)
                    .append(" Interrupted when triggerFlushAndAddMsg process for storekey ")
                    .append(storeKey).toString());
        } finally {
            writeCacheMutex.writeLock().unlock();
        }
        return false;
    }

    private void flush(final StringBuilder strBuffer) throws IOException {
        long startTime = System.currentTimeMillis();
        flushMutex.lock();
        this.lastMemFlushTime.set(System.currentTimeMillis());
        try {
            swapWriteCache(strBuffer);
            if (logger.isDebugEnabled()) {
                logger.debug(strBuffer.append("[Data Store] StoreKey=").append(storeKey)
                        .append(" Flushing entries.count:")
                        .append(msgMemStoreBeingFlush.getCurMsgCount())
                        .append(" -- getCachedSize ")
                        .append(msgMemStoreBeingFlush.getCurDataCacheSize() / 1024.0 / 1024)
                        .append(" Mb").toString());
                strBuffer.delete(0, strBuffer.length());
            }
        } catch (Throwable e) {
            if (e instanceof IOException) {
                throw (IOException) e;
            } else {
                throw new IOException(e);
            }
        } finally {
            try {
                isFlushOngoing.set(false);
            } finally {
                flushMutex.unlock();
                msgMemStatisInfo.addFlushTimeStatis(System.currentTimeMillis() - startTime);
                if (logger.isDebugEnabled()) {
                    logger.debug(strBuffer.append("[Data Store] StoreKey=")
                            .append(storeKey).append(" Flushed time : ")
                            .append(System.currentTimeMillis() - startTime).append(" ms").toString());
                    strBuffer.delete(0, strBuffer.length());
                }
            }
        }
    }

    private void swapWriteCache(final StringBuilder strBuffer) throws Throwable {
        writeCacheMutex.writeLock().lock();
        try {
            long lastDataPos = msgMemStore.getDataLastWritePos();
            long lastIndexPos = msgMemStore.getIndexLastWritePos();
            MsgMemStore tmp = msgMemStoreBeingFlush;
            msgMemStoreBeingFlush = msgMemStore;
            if (tmp.getMaxAllowedMsgCount() == writeCacheMaxCnt
                    && tmp.getMaxDataCacheSize() == writeCacheMaxSize) {
                msgMemStore = tmp;
                msgMemStore.clear();
            } else {
                tmp.close();
                msgMemStore =
                        new MsgMemStore(writeCacheMaxSize, writeCacheMaxCnt, tubeConfig);
                logger.info(strBuffer.append("[Data Store] Found ").append(getStoreKey())
                        .append(" Cache capacity change, new MemSize=")
                        .append(writeCacheMaxSize).append(", new CacheCnt=")
                        .append(writeCacheMaxCnt).toString());
                strBuffer.delete(0, strBuffer.length());
            }
            msgMemStore.resetStartPos(lastDataPos, lastIndexPos);
            hasFlushBeenTriggered.set(false);
            flushWriteCacheCondition.signalAll();
        } finally {
            try {
                isFlushOngoing.set(true);
            } finally {
                writeCacheMutex.writeLock().unlock();
            }
        }
        msgMemStoreBeingFlush.batchFlush(msgFileStore, strBuffer);
    }

}
