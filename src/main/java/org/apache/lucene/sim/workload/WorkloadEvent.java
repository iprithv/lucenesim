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

/**
 * Sealed interface for workload events fed into the simulation engine.
 */
public sealed interface WorkloadEvent {

    long wallClockMs();

    /**
     * A flush event: a new segment is created with the given size and doc count.
     * The engine generates segment names internally; this record contains only
     * the data needed for simulation.
     */
    record Flush(long rawSizeBytes, int maxDoc, long wallClockMs) implements WorkloadEvent {}

    /**
     * A delete event: delete {@code deleteCount} documents from the index.
     * Deletes are distributed across live segments proportionally to their live doc count.
     */
    record DeleteDocuments(int deleteCount, long wallClockMs) implements WorkloadEvent {}

    /**
     * A commit event: triggers {@code findFullFlushMerges}.
     */
    record Commit(long wallClockMs) implements WorkloadEvent {}

    /**
     * A force-merge event: triggers {@code findForcedMerges} with the given max segment count.
     */
    record ForceMerge(int maxSegmentCount, long wallClockMs) implements WorkloadEvent {}
}
