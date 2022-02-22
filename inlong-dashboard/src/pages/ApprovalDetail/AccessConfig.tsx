/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import React from 'react';
import { Divider, Table } from 'antd';
import i18n from '@/i18n';
import { genBusinessFields } from '@/components/AccessHelper';

const getBusinessContent = initialValues => [
  ...genBusinessFields(
    [
      'inlongGroupId',
      'cnName',
      'inCharges',
      'description',
      'middlewareType',
      'mqResourceObj',
      'queueModule',
      'topicPartitionNum',
      'dailyRecords',
      'dailyStorage',
      'peakRecords',
      'maxLength',
      'mqExtInfo.ensemble',
      'mqExtInfo.writeQuorum',
      'mqExtInfo.ackQuorum',
      'mqExtInfo.retentionTime',
      'mqExtInfo.ttl',
      'mqExtInfo.retentionSize',
    ],
    initialValues,
  ).map(item => {
    const obj = { ...item };
    if (typeof obj.suffix !== 'string') {
      delete obj.suffix;
    }
    delete obj.rules;
    delete obj.extra;

    if (typeof obj.type === 'string' || obj.name === 'inlongGroupId' || obj.name === 'inCharges') {
      obj.type = 'text';
    }

    return obj;
  }),
];

export const getFormContent = ({ isViwer, formData, suffixContent }) => {
  const array = [
    {
      type: (
        <Divider orientation="left">
          {i18n.t('pages.ApprovalDetail.AccessConfig.BasicInformation')}
        </Divider>
      ),
    },
    ...(getBusinessContent(formData.groupInfo) || []),
    {
      type: (
        <Divider orientation="left">
          {i18n.t('pages.ApprovalDetail.AccessConfig.DataFlowInformation')}
        </Divider>
      ),
    },
    {
      type: (
        <Table
          size="small"
          columns={[
            { title: 'ID', dataIndex: 'inlongStreamId' },
            {
              title: i18n.t('pages.ApprovalDetail.AccessConfig.DataStorages'),
              dataIndex: 'sinkList',
              render: text => text.map(item => item.sinkType).join(','),
            },
          ]}
          dataSource={formData?.streamInfoList || []}
          rowKey="id"
        />
      ),
    },
  ];

  return isViwer
    ? array
    : array.concat([
        {
          type: (
            <Divider orientation="left">
              {i18n.t('pages.ApprovalDetail.AccessConfig.ApprovalInformation')}
            </Divider>
          ),
        },
        ...suffixContent,
      ]);
};
