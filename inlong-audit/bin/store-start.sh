#!/bin/bash
#
#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#
bin_dir=$(dirname $0)
base_dir=`cd -P $bin_dir/..;pwd`
cd ..

PID=$(ps -ef | grep "inlong-audit" | grep -v grep | awk '{ print $2}')

if [ -n "$PID" ]; then
 echo "Application has already started."
 exit 0
fi

if [[ -z $JAVA_HOME ]]; then
    JAVA=$(which java)
    if [ $? != 0 ]; then
        echo "Error: JAVA_HOME not set, and no java executable found in $PATH." 1>&2
        exit 1
    fi
else
    JAVA=$JAVA_HOME/bin/java
fi

if [ ! -d "${base_dir}/logs" ]; then
  mkdir ${base_dir}/logs
fi

JAVA_OPTS="-server -Xms2g -Xmx2g -XX:SurvivorRatio=2 -XX:+UseParallelGC"

SERVERJAR=`ls -lt ${base_dir}/lib |grep audit-store | head -2 | tail -1 | awk '{print $NF}'`

nohup $JAVA $JAVA_OPTS -Dloader.path="$base_dir/conf,$base_dir/lib/" -jar "$base_dir/lib/$SERVERJAR" > $base_dir/logs/audit-store.log 2>&1 < /dev/null &

PIDFILE="$base_dir/bin/PID"

PID=$(ps -ef | grep "$base_dir" | grep -v grep | awk '{ print $2}')

sleep 3

if [ -n "$PID" ]; then
  echo -n $PID > "$PIDFILE"
  echo "Application started."
  exit 0
else
  echo "Application start failed."
  exit 0
fi