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

public final class SimSegment {

    public final String id;
    public final long rawSizeBytes;
    public final int maxDoc;
    public final int delCount;
    public final long createdAtTick;

    public SimSegment(String id, long rawSizeBytes, int maxDoc, int delCount, long createdAtTick) {
        if (rawSizeBytes < 0) throw new IllegalArgumentException("rawSizeBytes must be >= 0");
        if (maxDoc <= 0) throw new IllegalArgumentException("maxDoc must be > 0");
        if (delCount > maxDoc) throw new IllegalArgumentException("delCount > maxDoc");
        this.id = id;
        this.rawSizeBytes = rawSizeBytes;
        this.maxDoc = maxDoc;
        this.delCount = delCount;
        this.createdAtTick = createdAtTick;
    }

    public int liveDocs() {
        return maxDoc - delCount;
    }

    public double deletePct() {
        return (double) delCount / maxDoc;
    }

    public long liveProrationBytes() {
        return (long) (rawSizeBytes * (1.0 - deletePct()));
    }

    public SimSegment withDelCount(int newDelCount) {
        return new SimSegment(id, rawSizeBytes, maxDoc, newDelCount, createdAtTick);
    }

    @Override
    public String toString() {
        return id + "[" + liveDocs() + "/" + maxDoc + " docs, "
            + (rawSizeBytes / 1024) + "KB]";
    }
}
