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

public class MetricsCollector {

    private final WriteAmplificationCalculator wafCalc = new WriteAmplificationCalculator();
    private long totalMerges = 0;
    private int peakSegmentCount = 0;

    public void reset() {
        totalMerges = 0;
        peakSegmentCount = 0;
        wafCalc.reset();
    }

    public void onStart() {}

    public void onEnd() {}

    public void onTick(long tick, List<SimSegment> liveSegments) {
        peakSegmentCount = Math.max(peakSegmentCount, liveSegments.size());
    }

    public void onFlush(SimSegment seg, long tick) {
        wafCalc.onFlush(seg);
    }

    public void onMergeStart(List<SimSegment> inputs, long tick) {
        totalMerges++;
    }

    public void onMergeComplete(List<SimSegment> inputs, SimSegment output, long tick) {
        wafCalc.onMergeComplete(output);
    }

    public void onCommit(long tick, List<SimSegment> liveSegments) {}

    public org.apache.lucene.sim.engine.SimulationResult buildResult(List<SimSegment> finalSegments) {
        SimulationMetrics metrics = new SimulationMetrics(
            wafCalc.compute(),
            finalSegments.size(),
            totalMerges,
            wafCalc.totalFlushedBytes(),
            wafCalc.totalMergeOutputBytes(),
            peakSegmentCount
        );
        return new org.apache.lucene.sim.engine.SimulationResult(metrics, finalSegments);
    }
}
