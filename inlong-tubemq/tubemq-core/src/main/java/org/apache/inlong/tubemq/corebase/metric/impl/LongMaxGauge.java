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

package org.apache.inlong.tubemq.corebase.metric.impl;

import java.util.concurrent.atomic.AtomicLong;
import org.apache.inlong.tubemq.corebase.metric.Gauge;

/**
 * LongMaxGauge, store max value information.
 *
 * The metric includes a atomic long value, to store the current max value.
 */
public class LongMaxGauge extends BaseMetric implements Gauge {
    // value counter
    private final AtomicLong value = new AtomicLong(Long.MIN_VALUE);

    public LongMaxGauge(String metricName, String prefix) {
        super(metricName, prefix);
    }

    @Override
    public void update(long newValue) {
        while (true) {
            long cur = this.value.get();
            if (newValue <= cur) {
                break;
            }
            if (this.value.compareAndSet(cur, newValue)) {
                break;
            }
        }
    }

    @Override
    public void clear() {
        this.value.set(0L);
    }

    @Override
    public long getValue() {
        return this.value.get();
    }

    @Override
    public long getAndResetValue() {
        return this.value.getAndSet(Long.MIN_VALUE);
    }
}
