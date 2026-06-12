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

import java.util.List;

/**
 * Abstraction over merge execution strategy.
 *
 * <p>Implementations model <em>when</em> merges complete, not <em>what</em> they produce.
 * The {@link SimulationEngine} computes the merged {@link SimSegment} and passes it
 * to the scheduler, which decides when to invoke the callback.
 *
 * <p>Implementations:
 * <ul>
 *   <li>{@link SerialSimScheduler} - merges complete synchronously (same thread)</li>
 *   <li>{@link ConcurrentSimScheduler} - merges run asynchronously with simulated duration</li>
 * </ul>
 */
public interface SimScheduler {

    /**
     * Reset scheduler state for a new simulation run.
     * Called by {@link SimulationEngine#run()} before processing events.
     */
    void reset();

    /**
     * Schedule a merge. The scheduler decides when it completes.
     * For synchronous schedulers, the callback is invoked immediately.
     * For asynchronous schedulers, the callback is deferred.
     *
     * @param inputs  the segments being merged
     * @param output  the merged segment computed by the engine
     * @param callback invoked when the merge completes
     */
    void scheduleMerge(List<SimSegment> inputs, SimSegment output, MergeCallback callback);

    /**
     * Advance simulation time to the given tick.
     * Any merges that complete at or before this tick will have their callbacks invoked.
     *
     * @return true if any merges completed
     */
    boolean advanceToTick(long tick);

    /**
     * Wait for all pending merges to complete.
     */
    void drain();

    /**
     * Returns true if this scheduler executes merges synchronously
     * (i.e. callbacks are invoked before {@code scheduleMerge} returns).
     */
    boolean isSynchronous();

    @FunctionalInterface
    interface MergeCallback {
        void onComplete(SimSegment output);
    }
}
