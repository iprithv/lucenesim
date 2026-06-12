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
 * Strategy interface for estimating the on-disk size of a Lucene segment
 * given the number of documents it contains.
 *
 * <p>The simulator uses this to set {@code rawSizeBytes} on {@link SimSegment}
 * so that {@link org.apache.lucene.index.MergePolicy} sees the same size
 * distribution as real Lucene.
 */
public interface SegmentSizeEstimator {

    /**
     * Estimate raw on-disk bytes for a segment containing {@code docCount} documents.
     *
     * @param docCount number of live documents in the segment
     * @return estimated size in bytes (must be &gt;= 0)
     */
    long estimateSizeBytes(int docCount);
}
