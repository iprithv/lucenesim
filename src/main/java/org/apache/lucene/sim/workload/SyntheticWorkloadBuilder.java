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
package org.apache.lucene.sim.workload;

import org.apache.lucene.sim.calibration.SegmentSizeEstimator;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Builds synthetic workloads with configurable flush sizes, doc counts,
 * delete rates, and size variance.
 */
public class SyntheticWorkloadBuilder {

    private int flushCount = 100;
    private long flushSizeBytes = 1_000_000;
    private int flushDocCount = 10_000;
    private double deleteRate = 0.0;
    private long seed = 0;
    private SegmentSizeEstimator sizeEstimator = null;
    private double sizeVariance = 0.0;

    public static SyntheticWorkloadBuilder create() {
        return new SyntheticWorkloadBuilder();
    }

    public SyntheticWorkloadBuilder flushCount(int flushCount) {
        this.flushCount = flushCount;
        return this;
    }

    public SyntheticWorkloadBuilder flushSizeBytes(long flushSizeBytes) {
        this.flushSizeBytes = flushSizeBytes;
        return this;
    }

    public SyntheticWorkloadBuilder flushDocCount(int flushDocCount) {
        this.flushDocCount = flushDocCount;
        return this;
    }

    /**
     * Fraction of flushes that trigger a delete event.
     * Each delete event removes 1 document by default.
     * For bulk deletes, use {@link #deleteBatchSize(int)}.
     */
    public SyntheticWorkloadBuilder deleteRate(double deleteRate) {
        this.deleteRate = deleteRate;
        return this;
    }

    public SyntheticWorkloadBuilder seed(long seed) {
        this.seed = seed;
        return this;
    }

    /**
     * Use a {@link SegmentSizeEstimator} to compute per-flush segment sizes
     * instead of a hardcoded {@link #flushSizeBytes(long)} value.
     *
     * <p>If set, this takes precedence over {@code flushSizeBytes}.
     */
    public SyntheticWorkloadBuilder sizeEstimator(SegmentSizeEstimator estimator) {
        this.sizeEstimator = estimator;
        return this;
    }

    /**
     * Add random variance to flush sizes. 0.0 = uniform, 1.0 = up to +/- 100%.
     * Real workloads often have variable flush sizes (some small, some large).
     */
    public SyntheticWorkloadBuilder sizeVariance(double variance) {
        this.sizeVariance = variance;
        return this;
    }

    public WorkloadSource build() {
        List<WorkloadEvent> events = new ArrayList<>();
        Random rng = new Random(seed);
        int totalDocs = 0;

        for (int i = 0; i < flushCount; i++) {
            double variance = 1.0 + (rng.nextDouble() - 0.5) * 2 * sizeVariance;
            long sizeBytes = sizeEstimator != null
                ? sizeEstimator.estimateSizeBytes((int)(flushDocCount * variance))
                : (long)(flushSizeBytes * variance);
            int docCount = (int)(flushDocCount * variance);
            
            events.add(new WorkloadEvent.Flush(sizeBytes, docCount, i * 1000L));
            totalDocs += docCount;

            if (deleteRate > 0 && i > 0 && rng.nextDouble() < deleteRate) {
                events.add(new WorkloadEvent.DeleteDocuments(1, i * 1000L + 500L));
            }
        }

        return new ListWorkloadSource(events);
    }

    private static class ListWorkloadSource implements WorkloadSource {
        private final List<WorkloadEvent> events;

        ListWorkloadSource(List<WorkloadEvent> events) {
            this.events = events;
        }

        @Override
        public Iterator<WorkloadEvent> iterator() {
            return events.iterator();
        }


    }
}
