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

package org.apache.inlong.agent.metrics.audit;

import org.apache.commons.lang3.StringUtils;
import org.apache.inlong.agent.conf.AgentConfiguration;
import org.apache.inlong.audit.AuditImp;
import org.apache.inlong.audit.util.AuditConfig;

import java.util.HashSet;

import static org.apache.inlong.agent.constants.AgentConstants.AUDIT_ENABLE;
import static org.apache.inlong.agent.constants.AgentConstants.DEFAULT_AUDIT_ENABLE;
import static org.apache.inlong.agent.constants.AgentConstants.AUDIT_KEY_PROXYS;
import static org.apache.inlong.agent.constants.AgentConstants.DEFAULT_AUDIT_PROXYS;

/**
 * AuditUtils
 */
public class AuditUtils {

    public static final String AUDIT_KEY_FILE_PATH = "audit.filePath";
    public static final String AUDIT_DEFAULT_FILE_PATH = "/data/inlong/audit/";
    public static final String AUDIT_KEY_MAX_CACHE_ROWS = "audit.maxCacheRows";
    public static final int AUDIT_DEFAULT_MAX_CACHE_ROWS = 2000000;
    public static final int AUDIT_ID_AGENT_READ_SUCCESS = 3;
    public static final int AUDIT_ID_AGENT_SEND_SUCCESS = 4;

    private static boolean IS_AUDIT = true;

    /**
     * initAudit
     */
    public static void initAudit() {
        AgentConfiguration conf = AgentConfiguration.getAgentConf();
        // IS_AUDIT
        IS_AUDIT = conf.getBoolean(AUDIT_ENABLE, DEFAULT_AUDIT_ENABLE);
        if (IS_AUDIT) {
            // AuditProxy
            String strIpPorts = conf.get(AUDIT_KEY_PROXYS, DEFAULT_AUDIT_PROXYS);
            HashSet<String> proxys = new HashSet<>();
            if (!StringUtils.isBlank(strIpPorts)) {
                String[] ipPorts = strIpPorts.split("\\s+");
                for (String ipPort : ipPorts) {
                    proxys.add(ipPort);
                }
            }
            AuditImp.getInstance().setAuditProxy(proxys);
            // AuditConfig
            String filePath = conf.get(AUDIT_KEY_FILE_PATH, AUDIT_DEFAULT_FILE_PATH);
            int maxCacheRow = conf.getInt(AUDIT_KEY_MAX_CACHE_ROWS, AUDIT_DEFAULT_MAX_CACHE_ROWS);
            AuditConfig auditConfig = new AuditConfig(filePath, maxCacheRow);
            AuditImp.getInstance().setAuditConfig(auditConfig);
        }
    }

    /**
     * add
     *
     * @param auditID
     */
    public static void add(int auditID, String inlongGroupId, String inlongStreamId, long logTime) {
        if (!IS_AUDIT) {
            return;
        }
        AuditImp.getInstance().add(auditID, inlongGroupId, inlongStreamId, logTime, 1, 0);
    }

    /**
     * sendReport
     */
    public static void sendReport() {
        if (!IS_AUDIT) {
            return;
        }
        AuditImp.getInstance().sendReport();
    }
}
