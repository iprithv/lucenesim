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

import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.SegmentReader;
import org.apache.lucene.index.SerialMergeScheduler;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Calibrates a {@link SegmentSizeEstimator} by running a real {@link IndexWriter}
 * with sample documents and measuring actual segment sizes.
 *
 * <p>The calibrator:
 * <ol>
 *   <li>Indexes batches of documents at different sizes (1, 10, 100, 1000, 10000, ...)
 *   <li>Flushes after each batch (no merging - uses {@link NoMergePolicy})
 *   <li>Measures {@code SegmentCommitInfo.sizeInBytes()} for each resulting segment
 *   <li>Fits a linear model: {@code sizeBytes = bytesPerDoc * docCount + fixedOverhead}
 * </ol>
 */
public final class RealLuceneCalibrator {

    private RealLuceneCalibrator() {}

    /**
     * Calibrate a size estimator for the given document factory.
     *
     * @param factory    produces sample documents
     * @param sampleSizes doc counts to sample (e.g. 1, 10, 100, 1000, 10000)
     * @return a {@link ConstantSizeEstimator} fitted to measured data
     * @throws IOException if indexing fails
     */
    public static ConstantSizeEstimator calibrate(DocumentFactory factory, int... sampleSizes)
        throws IOException {

        if (sampleSizes.length < 2) {
            throw new IllegalArgumentException("Need at least 2 sample sizes for regression");
        }

        List<Measurement> measurements = new ArrayList<>();

        for (int docCount : sampleSizes) {
            long sizeBytes = measureSegmentSize(factory, docCount);
            measurements.add(new Measurement(docCount, sizeBytes));
        }

        return fitLinearModel(measurements);
    }

    /**
     * Convenience overload with default sample sizes.
     */
    public static ConstantSizeEstimator calibrate(DocumentFactory factory) throws IOException {
        return calibrate(factory, 1, 10, 100, 1000, 10000);
    }

    private static long measureSegmentSize(DocumentFactory factory, int docCount)
        throws IOException {

        Directory dir = new ByteBuffersDirectory();
        IndexWriterConfig iwc = new IndexWriterConfig()
            .setMergePolicy(NoMergePolicy.INSTANCE)
            .setMergeScheduler(new SerialMergeScheduler())
            .setMaxBufferedDocs(docCount + 1); // ensure single segment

        try (IndexWriter writer = new IndexWriter(dir, iwc)) {
            for (int i = 0; i < docCount; i++) {
                writer.addDocument(factory.create(i));
            }
            writer.flush();
            writer.commit();

            // Open reader and measure the single segment's size
            try (var reader = org.apache.lucene.index.DirectoryReader.open(dir)) {
                long totalSize = 0;
                for (LeafReaderContext ctx : reader.leaves()) {
                    SegmentReader sr = (SegmentReader) ctx.reader();
                    totalSize += sr.getSegmentInfo().sizeInBytes();
                }
                return totalSize;
            }
        }
    }

    private static ConstantSizeEstimator fitLinearModel(List<Measurement> measurements) {
        // Simple linear regression: y = a * x + b
        // Using least squares
        int n = measurements.size();
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;

        for (Measurement m : measurements) {
            sumX += m.docCount;
            sumY += m.sizeBytes;
            sumXY += (double) m.docCount * m.sizeBytes;
            sumX2 += (double) m.docCount * m.docCount;
        }

        double denominator = n * sumX2 - sumX * sumX;
        if (denominator == 0) {
            // All sample sizes are the same - fall back to average
            long avgBytesPerDoc = (long) (sumY / sumX);
            return new ConstantSizeEstimator(avgBytesPerDoc, 0);
        }

        double slope = (n * sumXY - sumX * sumY) / denominator;
        double intercept = (sumY - slope * sumX) / n;

        long bytesPerDoc = Math.max(0, (long) slope);
        long fixedOverhead = Math.max(0, (long) intercept);

        return new ConstantSizeEstimator(bytesPerDoc, fixedOverhead);
    }

    private record Measurement(int docCount, long sizeBytes) {}
}
