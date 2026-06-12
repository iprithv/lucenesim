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
package org.apache.lucene.sim.metrics;

import org.apache.lucene.sim.engine.SimSegment;

import java.util.List;

/**
 * Computes Write Amplification Factor (WAF) for a simulated indexing run.
 *
 * <p>Standard WAF formula:
 * <pre>
 *   WAF = totalBytesWritten / totalBytesOfUserData
 *       = (flushedBytes + mergeOutputBytes) / flushedBytes
 * </pre>
 *
 * <p>Where {@code mergeOutputBytes} is the sum of the <em>output</em> segment sizes
 * produced by all merges (prorated for deletes), not the input sizes.
 */
public class WriteAmplificationCalculator {

    private long totalFlushedBytes = 0;
    private long totalMergeOutputBytes = 0;

    public void onFlush(SimSegment seg) {
        totalFlushedBytes += seg.rawSizeBytes;
    }

    public void onMergeComplete(SimSegment output) {
        totalMergeOutputBytes += output.rawSizeBytes;
    }

    public double compute() {
        if (totalFlushedBytes == 0) return 1.0;
        return (double) (totalFlushedBytes + totalMergeOutputBytes) / totalFlushedBytes;
    }

    public long totalFlushedBytes() { return totalFlushedBytes; }
    public long totalMergeOutputBytes() { return totalMergeOutputBytes; }

    public void reset() {
        totalFlushedBytes = 0;
        totalMergeOutputBytes = 0;
    }
}
