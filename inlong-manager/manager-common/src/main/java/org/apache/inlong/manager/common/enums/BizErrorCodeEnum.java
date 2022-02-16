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

package org.apache.inlong.manager.common.enums;

public enum BizErrorCodeEnum {
    AUTHORIZATION_FAILED(2001, "Authentication failed"),
    INVALID_PARAMETER(2002, "The parameter is invalid"),
    PERMISSION_REQUIRED(2003, "The current user does not have operation authority"),
    AUTHENTICATION_REQUIRED(2004, "Authentication failed"),

    USER_IS_NOT_MANAGER(110, "%s is not the manager, please contact %s"),

    BUSINESS_NOT_FOUND(1001, "Business does not exist/no operation authority"),
    BUSINESS_DUPLICATE(1002, "Business already exists"),
    BUSINESS_SAVE_FAILED(1003, "Failed to save/update business information"),
    BUSINESS_PERMISSION_DENIED(1004, "No access to this business"),
    BUSINESS_HAS_DATA_STREAM(1005, "There are some valid data stream for this business"),
    BUSINESS_UPDATE_NOT_ALLOWED(1006, "The current business status does not support modification"),
    BUSINESS_DELETE_NOT_ALLOWED(1007, "The current business status does not support deletion"),
    BUSINESS_GROUP_ID_UPDATE_NOT_ALLOWED(1008, "The current business status does not support modifying the group id"),
    BUSINESS_MIDDLEWARE_UPDATE_NOT_ALLOWED(1011,
            "The current business status does not support modifying the middleware type"),

    MIDDLEWARE_TYPE_NOT_SUPPORTED(1021, "MIDDLEWARE_TYPE_NOT_SUPPORTED"),

    CLUSTER_NOT_FOUND(1101, "Cluster information does not exist"),

    DATA_STREAM_NOT_FOUND(1201, "Data stream does not exist/no operation permission"),
    DATA_STREAM_ID_DUPLICATE(1202, "The current business has a data stream with the same ID"),
    DATA_STREAM_OPT_NOT_ALLOWED(1203,
            "The current business status does not allow adding/modifying/deleting data streams"),
    DATA_STREAM_ID_UPDATE_NOT_ALLOWED(1205,
            "The current business status does not allow to modify the group or stream id"),
    DATA_STREAM_SOURCE_UPDATE_NOT_ALLOWED(1206,
            "The current business status does not allow to modify the data source type of the data stream"),
    DATA_STREAM_EXT_SAVE_FAILED(1207, "Failed to save/update data stream extension information"),
    DATA_STREAM_FIELD_SAVE_FAILED(1208, "Failed to save/update data stream field"),
    DATA_STREAM_DELETE_HAS_SOURCE(1209,
            "The data stream contains data source information and is not allowed to be deleted"),
    DATA_STREAM_DELETE_HAS_STORAGE(1210,
            "The data stream contains data storage information and is not allowed to be deleted"),

    DATA_SOURCE_DUPLICATE(1301, "Data source already exists"),
    DATA_SOURCE_BASIC_NOT_FOUND(1302, "The basic information of the data source does not exist"),
    DATA_SOURCE_DETAIL_NOT_FOUND(1303, "Data source detailed information does not exist"),
    DATA_SOURCE_TYPE_NOT_SUPPORTED(1304, "Data source type is not supported"),
    DATA_SOURCE_BASIC_DELETE_HAS_DETAIL(1305,
            "The data source contains detailed information and is not allowed to be deleted"),
    DATA_SOURCE_OPT_NOT_ALLOWED(1306,
            "The current business status does not allow adding/modifying/deleting data source information"),

    HIVE_OPERATION_FAILED(1311, "Hive operation failed"),

    STORAGE_TYPE_IS_NULL(1400, "Storage type is null"),
    STORAGE_TYPE_NOT_SUPPORT(1401, "Storage type '%s' not support"),
    STORAGE_INFO_NOT_FOUND(1402, "Storage information does not exist/no operation authority"),
    STORAGE_INFO_INCORRECT(1402, "Storage information was incorrect"),
    STORAGE_ALREADY_EXISTS(1403, "Storage already exist with the groupId and streamId"),
    STORAGE_SAVE_FAILED(1404, "Failed to save or update storage info"),
    STORAGE_FIELD_SAVE_FAILED(1405, "Failed to save or update storage field"),
    STORAGE_OPT_NOT_ALLOWED(1406, "Current status does not allow add/modification/delete storage info"),
    STORAGE_DB_NAME_UPDATE_NOT_ALLOWED(1407, "Current status does not allow modification the database name"),
    STORAGE_TB_NAME_UPDATE_NOT_ALLOWED(1408, "Current status does not allow modification the table name"),
    STORAGE_FIELD_UPDATE_NOT_ALLOWED(1409, "Current status not allowed to modification/delete field"),

    WORKFLOW_EXE_FAILED(4000, "Workflow execution exception"),

    CONSUMER_GROUP_NAME_DUPLICATED(2600, "The consumer group already exists in the cluster"),
    CONSUMER_GROUP_CREATE_FAILED(2601, "Failed to create tube consumer group"),
    TUBE_GROUP_CREATE_FAILED(2602, "Create Tube consumer group failed"),
    PULSAR_GROUP_CREATE_FAILED(2603, "Create Pulsar consumer group failed"),
    TUBE_TOPIC_CREATE_FAILED(2604, "CreateTube Topic failed"),
    PULSAR_TOPIC_CREATE_FAILED(2605, "Create Pulsar Topic failed"),
    PULSAR_DLQ_RLQ_ERROR(2606, "Wrong config for the RLQ and DLQ: RLQ was enabled, but the DLQ was disabled"),
    PULSAR_DLQ_DUPLICATED(2607, "DLQ topic already exists under the business"),
    PULSAR_RLQ_DUPLICATED(2608, "RLQ topic already exists under the business"),

    COMMON_FILE_DOWNLOAD_FAIL(6001, "File download failed"),
    COMMON_FILE_UPLOAD_FAIL(6002, "File upload failed"),
    ;

    private final int code;
    private final String message;

    BizErrorCodeEnum(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
