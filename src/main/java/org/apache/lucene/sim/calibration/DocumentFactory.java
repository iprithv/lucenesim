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

import org.apache.lucene.document.Document;

/**
 * Functional interface for creating sample documents during calibration.
 *
 * <p>The calibrator indexes documents produced by this factory and measures
 * the resulting segment sizes to build a {@link SegmentSizeEstimator}.
 */
@FunctionalInterface
public interface DocumentFactory {

    /**
     * Create a document with the given ID.
     *
     * @param docId sequential document identifier (0-based)
     * @return a Lucene {@link Document} ready for indexing
     */
    Document create(int docId);
}
