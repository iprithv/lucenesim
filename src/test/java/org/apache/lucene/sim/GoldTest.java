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

import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.SerialMergeScheduler;
import org.apache.lucene.index.TieredMergePolicy;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.index.Term;
import org.junit.jupiter.api.Test;
import org.apache.lucene.sim.calibration.ConstantSizeEstimator;
import org.apache.lucene.sim.calibration.RealLuceneCalibrator;
import org.apache.lucene.sim.engine.SerialSimScheduler;
import org.apache.lucene.sim.engine.SimulationEngine;
import org.apache.lucene.sim.engine.SimulationResult;
import org.apache.lucene.sim.metrics.MetricsCollector;
import org.apache.lucene.sim.workload.SyntheticWorkloadBuilder;
import org.apache.lucene.sim.workload.WorkloadSource;

import java.io.IOException;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GoldTest {

    @Test
    void simulatorMatchesRealIndexWriter() throws IOException {
        // -- A: Real IndexWriter --
        Directory dir = new ByteBuffersDirectory();
        TieredMergePolicy policy = new TieredMergePolicy();
        IndexWriterConfig config = new IndexWriterConfig(new WhitespaceAnalyzer())
            .setMergePolicy(policy)
            .setMaxBufferedDocs(10000)
            .setMergeScheduler(new SerialMergeScheduler());
        IndexWriter writer = new IndexWriter(dir, config);

        Random rng = new Random(42);
        int totalDocsAdded = 0;
        for (int flush = 0; flush < 100; flush++) {
            for (int i = 0; i < 10_000; i++) {
                Document doc = new Document();
                doc.add(new TextField("f", "doc " + totalDocsAdded++, Field.Store.NO));
                writer.addDocument(doc);
            }
            writer.flush();
            if (flush > 0 && rng.nextDouble() < 0.1) {
                int targetDoc = rng.nextInt(totalDocsAdded);
                writer.deleteDocuments(new Term("f", "doc " + targetDoc));
            }
        }
        writer.commit();

        // Get segment count via public DirectoryReader API
        int realSegmentCount;
        try (DirectoryReader dr = DirectoryReader.open(dir)) {
            realSegmentCount = dr.leaves().size();
        }
        writer.close();

        // -- B: Calibrate size estimator against the same document pattern --
        ConstantSizeEstimator sizeEstimator = RealLuceneCalibrator.calibrate(
            docId -> {
                Document doc = new Document();
                doc.add(new TextField("f", "doc " + docId, Field.Store.NO));
                return doc;
            }
        );

        // -- C: Simulator with calibrated sizes --
        WorkloadSource workload = SyntheticWorkloadBuilder.create()
            .flushCount(100)
            .flushDocCount(10_000)
            .deleteRate(0.10)
            .seed(42)
            .sizeEstimator(sizeEstimator)
            .build();

        SimulationResult simResult = new SimulationEngine(
            workload,
            new TieredMergePolicy(),
            new SerialSimScheduler(),
            new MetricsCollector(),
            false
        ).run();

        int simSegmentCount = simResult.metrics().finalSegmentCount();
        assertEquals(realSegmentCount, simSegmentCount, 1,
            "Sim segment count " + simSegmentCount + " vs real " + realSegmentCount);
    }
}
