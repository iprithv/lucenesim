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

import org.apache.lucene.index.MergePolicy;
import org.apache.lucene.index.MergeTrigger;
import org.apache.lucene.index.SegmentCommitInfo;
import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.sim.metrics.MetricsCollector;
import org.apache.lucene.sim.workload.WorkloadEvent;
import org.apache.lucene.sim.workload.WorkloadSource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

public class SimulationEngine {

    private final WorkloadSource workload;
    private final MergePolicy policy;
    private final SimScheduler scheduler;
    private final MetricsCollector metrics;
    private final boolean debug;

    // Per-simulation state - fresh for each run()
    private List<SimSegment> liveSegments;
    private Map<String, SegmentCommitInfo> sciBySegId;
    private Set<String> mergingIds;
    private SimInfrastructure.SimDirectory directory;
    private long tick;
    private int segCounter;
    private int totalDocsAdded;
    private final long deleteRngSeed;
    private Random deleteRng;

    public SimulationEngine(WorkloadSource workload, MergePolicy policy,
                            SimScheduler scheduler, MetricsCollector metrics, boolean debug) {
        this(workload, policy, scheduler, metrics, debug, 42);
    }

    public SimulationEngine(WorkloadSource workload, MergePolicy policy,
                            SimScheduler scheduler, MetricsCollector metrics,
                            boolean debug, long deleteRngSeed) {
        this.workload = workload;
        this.policy = policy;
        this.scheduler = scheduler;
        this.metrics = metrics;
        this.debug = debug;
        this.deleteRngSeed = deleteRngSeed;
    }

    public SimulationResult run() {
        // Fresh state for each run - no global mutable singletons
        this.liveSegments = new ArrayList<>();
        this.sciBySegId = new HashMap<>();
        this.mergingIds = new HashSet<>();
        this.directory = new SimInfrastructure.SimDirectory();
        this.tick = 0;
        this.segCounter = 0;
        this.totalDocsAdded = 0;
        this.deleteRng = new Random(deleteRngSeed);

        scheduler.reset();
        metrics.onStart();
        metrics.reset();

        for (WorkloadEvent event : workload) {
            tick = event.wallClockMs();

            // Process any merges that completed before this event
            scheduler.advanceToTick(tick);

            switch (event) {
                case WorkloadEvent.Flush e -> handleFlush(e);
                case WorkloadEvent.DeleteDocuments e -> handleDeleteDocuments(e);
                case WorkloadEvent.Commit e -> handleCommit(e);
                case WorkloadEvent.ForceMerge e -> handleForceMerge(e);
            }
            metrics.onTick(tick, Collections.unmodifiableList(liveSegments));
        }

        scheduler.drain();
        metrics.onEnd();
        return metrics.buildResult(Collections.unmodifiableList(liveSegments));
    }

    private void handleFlush(WorkloadEvent.Flush event) {
        String segName = nextSegName();
        SimSegment newSeg = new SimSegment(
            segName,
            event.rawSizeBytes(),
            event.maxDoc(),
            0,
            tick
        );
        totalDocsAdded += event.maxDoc();
        addSegment(newSeg);
        metrics.onFlush(newSeg, tick);

        if (scheduler.isSynchronous()) {
            invokeAndCascade(MergeTrigger.SEGMENT_FLUSH);
        } else {
            maybeScheduleMerges(MergeTrigger.SEGMENT_FLUSH);
        }
    }

    /**
     * Distribute deletes across live segments proportionally to their live doc count.
     *
     * <p>In real Lucene, {@code deleteDocuments(Term)} buffers deletes which are applied
     * to segment liveDocs on the next flush. The merge policy sees the updated delete
     * counts via {@code findMerges(SEGMENT_FLUSH)} - {@code findForcedDeletesMerges}
     * is only called when the user explicitly calls {@code IndexWriter.forceMergeDeletes()}.
     *
     * <p><b>Note on approximation:</b> Real Lucene's {@code deleteDocuments(Term)} deletes
     * exactly the document(s) matching the term, which typically targets a single segment.
     * The simulator distributes deletes randomly weighted by live doc count. This is a
     * reasonable approximation for aggregate metrics (segment count, WAF) but will diverge
     * for workloads with heavy deletes. See {@code GoldTest} for validation at low delete rates.
     */
    private void handleDeleteDocuments(WorkloadEvent.DeleteDocuments event) {
        int remainingDeletes = event.deleteCount();

        while (remainingDeletes > 0) {
            // Build a weighted list of segments that can accept more deletes
            List<SimSegment> candidates = liveSegments.stream()
                .filter(s -> s.delCount < s.maxDoc)
                .collect(Collectors.toList());

            if (candidates.isEmpty()) break;

            // Pick a segment weighted by live doc count
            int totalLive = candidates.stream().mapToInt(SimSegment::liveDocs).sum();
            if (totalLive == 0) break;

            int target = deleteRng.nextInt(totalLive);
            int cumulative = 0;
            SimSegment chosen = candidates.get(0);
            for (SimSegment seg : candidates) {
                cumulative += seg.liveDocs();
                if (cumulative > target) {
                    chosen = seg;
                    break;
                }
            }

            // Apply one delete to the chosen segment
            int newDel = Math.min(chosen.maxDoc, chosen.delCount + 1);
            SimSegment updated = chosen.withDelCount(newDel);
            int idx = liveSegments.indexOf(chosen);
            if (idx >= 0) {
                liveSegments.set(idx, updated);
                sciBySegId.put(updated.id, SimInfrastructure.buildSCI(updated, directory));
            }
            remainingDeletes--;
        }

        // No findForcedDeletesMerges here - in real Lucene, deleteDocuments()
        // never triggers findForcedDeletesMerges. Deletes are picked up by the
        // regular findMerges(SEGMENT_FLUSH) on the next flush. Only an explicit
        // IndexWriter.forceMergeDeletes() call triggers findForcedDeletesMerges.
    }

    private void handleCommit(WorkloadEvent.Commit event) {
        metrics.onCommit(tick, Collections.unmodifiableList(liveSegments));
        try {
            SegmentInfos infos = buildSegmentInfos();
            SimMergeContext ctx = buildMergeContext();
            MergePolicy.MergeSpecification spec = policy.findFullFlushMerges(MergeTrigger.FULL_FLUSH, infos, ctx);
            if (spec != null && !spec.merges.isEmpty()) {
                scheduleMerges(spec);
            }
        } catch (IOException e) {
            throw new SimulationException("findFullFlushMerges threw", e);
        }
    }

    private void handleForceMerge(WorkloadEvent.ForceMerge event) {
        try {
            SegmentInfos infos = buildSegmentInfos();
            SimMergeContext ctx = buildMergeContext();
            Map<SegmentCommitInfo, Boolean> allSegs = new HashMap<>();
            for (SegmentCommitInfo sci : infos) {
                allSegs.put(sci, Boolean.TRUE);
            }
            MergePolicy.MergeSpecification spec = policy.findForcedMerges(infos, event.maxSegmentCount(), allSegs, ctx);
            if (spec != null && !spec.merges.isEmpty()) {
                scheduleMerges(spec);
            }
        } catch (IOException e) {
            throw new SimulationException("findForcedMerges threw", e);
        }
    }

    /**
     * Synchronous cascade: call findMerges in a loop until no more merges are found.
     * Used only with {@link SerialSimScheduler}.
     */
    private void invokeAndCascade(MergeTrigger trigger) {
        int cascadeDepth = 0;
        MergeTrigger currentTrigger = trigger;
        while (true) {
            MergePolicy.MergeSpecification spec;
            try {
                spec = policy.findMerges(currentTrigger, buildSegmentInfos(), buildMergeContext());
            } catch (IOException e) {
                throw new SimulationException("findMerges threw", e);
            }
            if (spec == null || spec.merges.isEmpty()) break;

            scheduleMerges(spec);

            currentTrigger = MergeTrigger.MERGE_FINISHED;
            if (++cascadeDepth > 1000) {
                throw new SimulationException("Cascade depth > 1000 - likely infinite loop in policy", null);
            }
        }
    }

    /**
     * Single-shot merge scheduling. Used with asynchronous schedulers.
     * The callback will trigger subsequent findMerges(MERGE_FINISHED) when the merge completes.
     */
    private void maybeScheduleMerges(MergeTrigger trigger) {
        try {
            MergePolicy.MergeSpecification spec = policy.findMerges(trigger, buildSegmentInfos(), buildMergeContext());
            if (spec != null && !spec.merges.isEmpty()) {
                scheduleMerges(spec);
            }
        } catch (IOException e) {
            throw new SimulationException("findMerges threw", e);
        }
    }

    private void scheduleMerges(MergePolicy.MergeSpecification spec) {
        for (MergePolicy.OneMerge merge : spec.merges) {
            List<SimSegment> inputs = resolveInputs(merge);
            if (inputs.isEmpty()) continue;

            inputs.forEach(s -> mergingIds.add(s.id));
            metrics.onMergeStart(inputs, tick);

            SimSegment output = computeMergeOutput(inputs, nextSegName(), tick);

            SimScheduler.MergeCallback callback = scheduler.isSynchronous()
                ? completedOutput -> finalizeMerge(inputs, completedOutput)
                : completedOutput -> {
                    finalizeMerge(inputs, completedOutput);
                    maybeScheduleMerges(MergeTrigger.MERGE_FINISHED);
                };

            scheduler.scheduleMerge(inputs, output, callback);
        }
    }

    private void finalizeMerge(List<SimSegment> inputs, SimSegment output) {
        for (SimSegment input : inputs) {
            liveSegments.remove(input);
            sciBySegId.remove(input.id);
            directory.deregisterSegment(input.id);
            mergingIds.remove(input.id);
        }
        addSegment(output);
        metrics.onMergeComplete(inputs, output, tick);
    }

    private void addSegment(SimSegment seg) {
        liveSegments.add(seg);
        SegmentCommitInfo sci = SimInfrastructure.buildSCI(seg, directory);
        sciBySegId.put(seg.id, sci);
    }

    private SegmentInfos buildSegmentInfos() {
        SegmentInfos infos = new SegmentInfos(org.apache.lucene.util.Version.LATEST.major);
        for (SimSegment seg : liveSegments) {
            infos.add(sciBySegId.get(seg.id));
        }
        return infos;
    }

    private SimMergeContext buildMergeContext() {
        Set<SegmentCommitInfo> merging = liveSegments.stream()
            .filter(s -> mergingIds.contains(s.id))
            .map(s -> sciBySegId.get(s.id))
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        return new SimMergeContext(merging, debug ? System.err : null);
    }

    private List<SimSegment> resolveInputs(MergePolicy.OneMerge merge) {
        Map<String, SimSegment> byName = liveSegments.stream()
            .collect(Collectors.toMap(s -> s.id, s -> s));
        return merge.segments.stream()
            .map(sci -> byName.get(sci.info.name))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    private SimSegment computeMergeOutput(List<SimSegment> inputs, String outputId, long atTick) {
        int totalLiveDocs = inputs.stream().mapToInt(SimSegment::liveDocs).sum();
        long outputBytes = inputs.stream().mapToLong(SimSegment::liveProrationBytes).sum();
        return new SimSegment(outputId, outputBytes, totalLiveDocs, 0, atTick);
    }

    private String nextSegName() {
        return "_" + Long.toString(segCounter++, 36);
    }
}
