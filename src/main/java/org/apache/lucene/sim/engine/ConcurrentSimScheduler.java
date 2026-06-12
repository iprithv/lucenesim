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
package org.apache.lucene.sim.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Asynchronous merge scheduler that models concurrent merge execution.
 *
 * <p>Merges take time proportional to their input size and a configured throughput.
 * Up to {@code maxThreadCount} merges run concurrently; additional merges are queued
 * until a thread slot opens. This models
 * {@link org.apache.lucene.index.ConcurrentMergeScheduler}.
 */
public class ConcurrentSimScheduler implements SimScheduler {

    private final int maxThreadCount;
    private final int maxMergeCount;
    private final double throughputMBps;

    private long currentTick;

    // Running merges ordered by completion tick
    private final PriorityQueue<RunningMerge> runningMerges;

    // Pending merges (queued but not yet running)
    private final List<PendingMerge> pendingMerges;

    public ConcurrentSimScheduler() {
        this(Math.max(1, Runtime.getRuntime().availableProcessors() / 2), 100.0);
    }

    public ConcurrentSimScheduler(int maxThreadCount, double throughputMBps) {
        this(maxThreadCount, maxThreadCount + 5, throughputMBps);
    }

    public ConcurrentSimScheduler(int maxThreadCount, int maxMergeCount, double throughputMBps) {
        if (maxThreadCount < 1) {
            throw new IllegalArgumentException("maxThreadCount must be >= 1");
        }
        if (maxMergeCount < maxThreadCount) {
            throw new IllegalArgumentException("maxMergeCount must be >= maxThreadCount");
        }
        if (throughputMBps <= 0) {
            throw new IllegalArgumentException("throughputMBps must be > 0");
        }
        this.maxThreadCount = maxThreadCount;
        this.maxMergeCount = maxMergeCount;
        this.throughputMBps = throughputMBps;
        this.runningMerges = new PriorityQueue<>(java.util.Comparator.comparingLong(rm -> rm.completionTick));
        this.pendingMerges = new ArrayList<>();
        reset();
    }

    @Override
    public void reset() {
        this.currentTick = 0;
        this.runningMerges.clear();
        this.pendingMerges.clear();
    }

    @Override
    public void scheduleMerge(List<SimSegment> inputs, SimSegment output, MergeCallback callback) {
        long inputBytes = inputs.stream().mapToLong(s -> s.rawSizeBytes).sum();
        long durationTicks = Math.max(1, (long) (inputBytes / (throughputMBps * 1024 * 1024)));

        if (runningMerges.size() < maxThreadCount) {
            long completionTick = currentTick + durationTicks;
            runningMerges.add(new RunningMerge(output, completionTick, callback));
        } else if (runningMerges.size() + pendingMerges.size() < maxMergeCount) {
            pendingMerges.add(new PendingMerge(output, durationTicks, callback));
        } else {
            // Beyond maxMergeCount: queue anyway. In real Lucene this would stall the producer,
            // but for simulation we just let it queue - the merge policy behaviour is what matters.
            pendingMerges.add(new PendingMerge(output, durationTicks, callback));
        }
    }

    @Override
    public boolean advanceToTick(long targetTick) {
        boolean anyCompleted = false;

        while (!runningMerges.isEmpty() && runningMerges.peek().completionTick <= targetTick) {
            RunningMerge rm = runningMerges.poll();
            currentTick = rm.completionTick;
            promotePendingMerges();
            rm.callback.onComplete(rm.output);
            anyCompleted = true;
        }

        if (!anyCompleted) {
            currentTick = targetTick;
        }

        return anyCompleted;
    }

    @Override
    public void drain() {
        while (!runningMerges.isEmpty() || !pendingMerges.isEmpty()) {
            if (runningMerges.isEmpty()) {
                promotePendingMerges();
            }
            RunningMerge rm = runningMerges.poll();
            currentTick = rm.completionTick;
            promotePendingMerges();
            rm.callback.onComplete(rm.output);
        }
    }

    @Override
    public boolean isSynchronous() {
        return false;
    }

    private void promotePendingMerges() {
        while (!pendingMerges.isEmpty() && runningMerges.size() < maxThreadCount) {
            PendingMerge pm = pendingMerges.remove(0);
            long completionTick = currentTick + pm.durationTicks;
            runningMerges.add(new RunningMerge(pm.output, completionTick, pm.callback));
        }
    }

    // ------------------------------------------------------------------

    private record RunningMerge(
        SimSegment output,
        long completionTick,
        MergeCallback callback
    ) {}

    private record PendingMerge(
        SimSegment output,
        long durationTicks,
        MergeCallback callback
    ) {}
}
