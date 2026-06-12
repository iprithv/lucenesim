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
import org.apache.lucene.index.SegmentCommitInfo;
import org.apache.lucene.util.InfoStream;
import org.apache.lucene.util.PrintStreamInfoStream;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Collections;
import java.util.Set;

public class SimMergeContext implements MergePolicy.MergeContext {

    private final Set<SegmentCommitInfo> mergingSegments;
    private final PrintStream debugStream;

    public SimMergeContext(Set<SegmentCommitInfo> mergingSegments, PrintStream debugStream) {
        this.mergingSegments = Collections.unmodifiableSet(mergingSegments);
        this.debugStream = debugStream;
    }

    @Override
    public Set<SegmentCommitInfo> getMergingSegments() {
        return mergingSegments;
    }

    @Override
    public int numDeletesToMerge(SegmentCommitInfo info) throws IOException {
        return info.getDelCount();
    }

    @Override
    public int numDeletedDocs(SegmentCommitInfo info) {
        return info.getDelCount();
    }

    @Override
    public InfoStream getInfoStream() {
        return debugStream != null ? new PrintStreamInfoStream(debugStream) : InfoStream.NO_OUTPUT;
    }
}
