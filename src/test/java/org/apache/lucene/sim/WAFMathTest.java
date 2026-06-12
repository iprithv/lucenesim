/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.lucene.sim;

import org.apache.lucene.index.LogByteSizeMergePolicy;
import org.apache.lucene.index.MergePolicy;
import org.junit.jupiter.api.Test;
import org.apache.lucene.sim.engine.SerialSimScheduler;
import org.apache.lucene.sim.engine.SimScheduler;
import org.apache.lucene.sim.engine.SimulationEngine;
import org.apache.lucene.sim.engine.SimulationResult;
import org.apache.lucene.sim.metrics.MetricsCollector;
import org.apache.lucene.sim.metrics.SimulationMetrics;
import org.apache.lucene.sim.workload.SyntheticWorkloadBuilder;
import org.apache.lucene.sim.workload.WorkloadSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class WAFMathTest {

    @Test
    void binaryMergeTreeWAF() {
        WorkloadSource workload = SyntheticWorkloadBuilder.create()
            .flushCount(4)
            .flushSizeBytes(1_000_000)
            .flushDocCount(1000)
            .seed(0)
            .build();

        LogByteSizeMergePolicy policy = new LogByteSizeMergePolicy();
        // Lower the merge threshold so 1MB segments trigger merges
        policy.setMinMergeMB(0.5);
        policy.setMergeFactor(2);
        // With 4 equal-size segments of 1MB and mergeFactor=2:
        // Level 1: 2 merges produce 2 x 2MB output segments = 4MB written
        // Level 2: 1 merge produces 1 x 4MB output segment = 4MB written
        // Total flushed: 4MB
        // Total merge output: 8MB
        // WAF = (4MB flushed + 8MB merge output) / 4MB flushed = 3.0

        SimulationResult result = new SimulationEngine(
            workload,
            policy,
            new SerialSimScheduler(),
            new MetricsCollector(),
            false
        ).run();

        SimulationMetrics metrics = result.metrics();
        assertEquals(3.0, metrics.writeAmplificationFactor(), 0.01,
            "Expected WAF = 3.0 for 4 segments merged as binary tree, got " + metrics.writeAmplificationFactor());
    }
}
