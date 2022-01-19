#!/bin/sh
#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

file_path=$(cd "$(dirname "$0")"/../;pwd)
# config
conf_file=${file_path}/conf/application.properties

# replace the configuration
sed -i "s/127.0.0.1:3306/${JDBC_URL}/g" "${conf_file}"
sed -i "s/spring.datasource.druid.username=.*$/spring.datasource.druid.username=${USERNAME}/g" "${conf_file}"
sed -i "s/spring.datasource.druid.password=.*$/spring.datasource.druid.password=${PASSWORD}/g" "${conf_file}"

# start
bash +x ${file_path}/bin/start.sh
sleep 3
# keep alive
tail -F ${file_path}/logs/audit-store.log
