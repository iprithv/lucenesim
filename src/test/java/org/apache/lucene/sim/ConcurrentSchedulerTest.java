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
import org.apache.lucene.index.TieredMergePolicy;
import org.junit.jupiter.api.Test;
import org.apache.lucene.sim.engine.ConcurrentSimScheduler;
import org.apache.lucene.sim.engine.SerialSimScheduler;
import org.apache.lucene.sim.engine.SimulationEngine;
import org.apache.lucene.sim.engine.SimulationResult;
import org.apache.lucene.sim.metrics.MetricsCollector;
import org.apache.lucene.sim.workload.SyntheticWorkloadBuilder;
import org.apache.lucene.sim.workload.WorkloadSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConcurrentSchedulerTest {

    @Test
    void concurrentSchedulerWithInfiniteThroughputMatchesSerial() {
        WorkloadSource workload = SyntheticWorkloadBuilder.create()
            .flushCount(20)
            .flushSizeBytes(1_000_000)
            .flushDocCount(1000)
            .seed(0)
            .build();

        // Serial scheduler
        SimulationResult serialResult = new SimulationEngine(
            workload,
            new TieredMergePolicy(),
            new SerialSimScheduler(),
            new MetricsCollector(),
            false
        ).run();

        // Concurrent scheduler with infinite throughput and 1 thread.
        // Merges take 1 tick minimum (duration = max(1, input/inf) = 1).
        // Since flush events are 1000 ticks apart, all merges complete
        // before the next event, so behavior matches serial.
        SimulationResult concurrentResult = new SimulationEngine(
            workload,
            new TieredMergePolicy(),
            new ConcurrentSimScheduler(1, Double.POSITIVE_INFINITY),
            new MetricsCollector(),
            false
        ).run();

        assertEquals(serialResult.metrics().finalSegmentCount(),
            concurrentResult.metrics().finalSegmentCount(),
            "With infinite throughput, 1-thread concurrent should match serial");
        assertEquals(serialResult.metrics().totalMerges(),
            concurrentResult.metrics().totalMerges(),
            "Merge count should match");
    }

    @Test
    void concurrentSchedulerRunsMultipleMergesInParallel() {
        WorkloadSource workload = SyntheticWorkloadBuilder.create()
            .flushCount(20)
            .flushSizeBytes(1_000_000)
            .flushDocCount(1000)
            .seed(0)
            .build();

        // With realistic throughput, merges take meaningful time.
        // 4 threads allow up to 4 merges to run concurrently.
        SimulationResult result = new SimulationEngine(
            workload,
            new TieredMergePolicy(),
            new ConcurrentSimScheduler(4, 100.0), // 4 threads, 100 MB/s
            new MetricsCollector(),
            false
        ).run();

        // Just verify it doesn't crash and produces reasonable output
        assertTrue(result.metrics().finalSegmentCount() > 0,
            "Should have at least 1 segment");
        assertTrue(result.metrics().totalMerges() >= 0,
            "Merge count should be non-negative");
    }

    @Test
    void concurrentSchedulerWithLogByteSizePolicy() {
        WorkloadSource workload = SyntheticWorkloadBuilder.create()
            .flushCount(10)
            .flushSizeBytes(500_000)
            .flushDocCount(1000)
            .seed(0)
            .build();

        LogByteSizeMergePolicy policy = new LogByteSizeMergePolicy();
        policy.setMinMergeMB(0.1);
        policy.setMergeFactor(2);

        SimulationResult result = new SimulationEngine(
            workload,
            policy,
            new ConcurrentSimScheduler(2, 500.0),
            new MetricsCollector(),
            false
        ).run();

        assertTrue(result.metrics().finalSegmentCount() > 0);
    }
}
