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
package org.apache.lucene.sim.calibration;

/**
 * Simple linear size estimator: {@code size = bytesPerDoc * docCount + fixedOverhead}.
 *
 * <p>This model is accurate enough for most fixed-schema workloads because
 * Lucene segment size grows roughly linearly with document count.
 */
public final class ConstantSizeEstimator implements SegmentSizeEstimator {

    private final long bytesPerDoc;
    private final long fixedOverhead;

    public ConstantSizeEstimator(long bytesPerDoc, long fixedOverhead) {
        if (bytesPerDoc < 0) {
            throw new IllegalArgumentException("bytesPerDoc must be >= 0");
        }
        if (fixedOverhead < 0) {
            throw new IllegalArgumentException("fixedOverhead must be >= 0");
        }
        this.bytesPerDoc = bytesPerDoc;
        this.fixedOverhead = fixedOverhead;
    }

    @Override
    public long estimateSizeBytes(int docCount) {
        if (docCount < 0) {
            throw new IllegalArgumentException("docCount must be >= 0");
        }
        return fixedOverhead + bytesPerDoc * docCount;
    }

    public long bytesPerDoc() {
        return bytesPerDoc;
    }

    public long fixedOverhead() {
        return fixedOverhead;
    }

    @Override
    public String toString() {
        return "ConstantSizeEstimator{bytesPerDoc=" + bytesPerDoc
            + ", fixedOverhead=" + fixedOverhead + "}";
    }
}
