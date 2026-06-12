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
package org.apache.lucene.sim;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.TextField;
import org.junit.jupiter.api.Test;
import org.apache.lucene.sim.calibration.ConstantSizeEstimator;
import org.apache.lucene.sim.calibration.RealLuceneCalibrator;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class CalibrationTest {

    @Test
    void calibratorProducesReasonableEstimate() throws Exception {
        ConstantSizeEstimator estimator = RealLuceneCalibrator.calibrate(
            docId -> {
                Document doc = new Document();
                doc.add(new TextField("f", "doc " + docId, TextField.Store.NO));
                return doc;
            }
        );

        // For simple TextField documents, bytesPerDoc should be positive
        // and relatively small (tens to hundreds of bytes per doc)
        assertTrue(estimator.bytesPerDoc() > 0,
            "bytesPerDoc should be positive, got " + estimator.bytesPerDoc());
        assertTrue(estimator.bytesPerDoc() < 10_000,
            "bytesPerDoc seems too large: " + estimator.bytesPerDoc());

        // Fixed overhead should be non-negative
        assertTrue(estimator.fixedOverhead() >= 0,
            "fixedOverhead should be non-negative");

        // Estimate for 10_000 docs should be in a reasonable range
        long estimate = estimator.estimateSizeBytes(10_000);
        assertTrue(estimate > 0, "estimate should be positive");
        assertTrue(estimate < 10_000_000,
            "estimate for 10k docs seems too large: " + estimate);
    }

    @Test
    void calibratorWithCustomSampleSizes() throws Exception {
        ConstantSizeEstimator estimator = RealLuceneCalibrator.calibrate(
            docId -> {
                Document doc = new Document();
                doc.add(new TextField("f", "doc " + docId, TextField.Store.NO));
                return doc;
            },
            100, 1000, 5000
        );

        assertTrue(estimator.bytesPerDoc() > 0);
        // Estimates should increase monotonically
        long e100 = estimator.estimateSizeBytes(100);
        long e1000 = estimator.estimateSizeBytes(1000);
        long e5000 = estimator.estimateSizeBytes(5000);
        assertTrue(e100 < e1000, "estimate should grow with doc count");
        assertTrue(e1000 < e5000, "estimate should grow with doc count");
    }
}
