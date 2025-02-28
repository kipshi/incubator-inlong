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

package org.apache.inlong.tubemq.server.broker.stats;

import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.inlong.tubemq.corebase.TBaseConstants;
import org.junit.Assert;
import org.junit.Test;

/**
 * ServiceStatsHolder test.
 */
public class ServiceStatsHolderTest {

    @Test
    public void testServiceStatsHolder() {
        // add consumer online count add 3, dec 2
        ServiceStatsHolder.incConsumerOnlineCnt();
        ServiceStatsHolder.incConsumerOnlineCnt();
        ServiceStatsHolder.incConsumerOnlineCnt();
        ServiceStatsHolder.decConsumerOnlineCnt(true);
        ServiceStatsHolder.decConsumerOnlineCnt(false);
        // add hb exception, add 3
        ServiceStatsHolder.incBrokerHBExcCnt();
        ServiceStatsHolder.incBrokerHBExcCnt();
        ServiceStatsHolder.incBrokerHBExcCnt();
        // add master no node exception, add 2
        ServiceStatsHolder.incBrokerTimeoutCnt();
        ServiceStatsHolder.incBrokerTimeoutCnt();
        // add zk dlt time, add 3
        ServiceStatsHolder.updZKSyncDataDlt(30);
        ServiceStatsHolder.updZKSyncDataDlt(10);
        ServiceStatsHolder.updZKSyncDataDlt(50);
        // add zk exception, add 1
        ServiceStatsHolder.incZKExcCnt();
        // add disk dlt time, add 2
        ServiceStatsHolder.updDiskSyncDataDlt(100);
        ServiceStatsHolder.updDiskSyncDataDlt(10);
        // add IO exception, add 2
        ServiceStatsHolder.incDiskIOExcCnt();
        ServiceStatsHolder.incDiskIOExcCnt();
        // check result
        Map<String, Long> retMap = new LinkedHashMap<>();
        ServiceStatsHolder.getValue(retMap);
        Assert.assertEquals(1, retMap.get("consumer_online_cnt").longValue());
        Assert.assertEquals(1, retMap.get("consumer_timeout_cnt").longValue());
        Assert.assertEquals(3, retMap.get("broker_hb_exc_cnt").longValue());
        Assert.assertEquals(2, retMap.get("broker_timeout_cnt").longValue());
        Assert.assertEquals(3, retMap.get("zk_sync_dlt_count").longValue());
        Assert.assertEquals(10, retMap.get("zk_sync_dlt_min").longValue());
        Assert.assertEquals(50, retMap.get("zk_sync_dlt_max").longValue());
        Assert.assertEquals(1, retMap.get("zk_sync_dlt_cell_8t16").longValue());
        Assert.assertEquals(1, retMap.get("zk_sync_dlt_cell_16t32").longValue());
        Assert.assertEquals(1, retMap.get("zk_sync_dlt_cell_32t64").longValue());
        Assert.assertEquals(1, retMap.get("zk_exc_cnt").longValue());
        Assert.assertEquals(2, retMap.get("file_exc_cnt").longValue());
        Assert.assertEquals(2, retMap.get("file_sync_dlt_count").longValue());
        Assert.assertEquals(100, retMap.get("file_sync_dlt_max").longValue());
        Assert.assertEquals(10, retMap.get("file_sync_dlt_min").longValue());
        Assert.assertEquals(1, retMap.get("file_sync_dlt_cell_8t16").longValue());
        Assert.assertEquals(1, retMap.get("file_sync_dlt_cell_64t128").longValue());
        final long sinceTime1 = retMap.get("reset_time");
        // verify snapshot
        ServiceStatsHolder.snapShort(retMap);
        retMap.clear();
        // add consumer online count, add 1
        ServiceStatsHolder.incConsumerOnlineCnt();
        // add disk sync data, add 1
        ServiceStatsHolder.updDiskSyncDataDlt(999);
        ServiceStatsHolder.snapShort(retMap);
        Assert.assertNotEquals(sinceTime1, retMap.get("reset_time").longValue());
        Assert.assertEquals(2, retMap.get("consumer_online_cnt").longValue());
        Assert.assertEquals(0, retMap.get("consumer_timeout_cnt").longValue());
        Assert.assertEquals(0, retMap.get("broker_hb_exc_cnt").longValue());
        Assert.assertEquals(0, retMap.get("broker_timeout_cnt").longValue());
        Assert.assertEquals(0, retMap.get("zk_sync_dlt_count").longValue());
        Assert.assertEquals(Long.MAX_VALUE, retMap.get("zk_sync_dlt_min").longValue());
        Assert.assertEquals(Long.MIN_VALUE, retMap.get("zk_sync_dlt_max").longValue());
        Assert.assertEquals(0, retMap.get("zk_exc_cnt").longValue());
        Assert.assertEquals(0, retMap.get("file_exc_cnt").longValue());
        Assert.assertEquals(1, retMap.get("file_sync_dlt_count").longValue());
        Assert.assertEquals(999, retMap.get("file_sync_dlt_max").longValue());
        Assert.assertEquals(999, retMap.get("file_sync_dlt_min").longValue());
        Assert.assertEquals(1, retMap.get("file_sync_dlt_cell_512t1024").longValue());
        // get content by StringBuilder
        StringBuilder strBuff = new StringBuilder(TBaseConstants.BUILDER_DEFAULT_SIZE);
        ServiceStatsHolder.getValue(strBuff);
        // System.out.println(strBuff.toString());
    }
}
