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

import org.apache.lucene.codecs.Codec;
import org.apache.lucene.index.SegmentCommitInfo;
import org.apache.lucene.index.SegmentInfo;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.store.Lock;
import org.apache.lucene.store.NoLockFactory;
import org.apache.lucene.util.StringHelper;
import org.apache.lucene.util.Version;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for fake Lucene infrastructure used during simulation.
 *
 * <p>Each {@link SimulationEngine} creates its own {@link SimDirectory} instance
 * so that multiple simulations can run concurrently without corrupting each other.
 */
public final class SimInfrastructure {

    public static final Codec CODEC = Codec.getDefault();

    private SimInfrastructure() {}

    public static SegmentCommitInfo buildSCI(SimSegment seg, SimDirectory directory) {
        directory.registerSegmentSize(seg.id + ".cfs", seg.rawSizeBytes);

        SegmentInfo si = new SegmentInfo(
            directory,
            Version.LATEST,
            Version.LATEST,
            seg.id,
            seg.maxDoc,
            false,
            false,
            CODEC,
            Collections.emptyMap(),
            StringHelper.randomId(),
            Collections.emptyMap(),
            null
        );
        si.setFiles(Set.of(seg.id + ".cfs"));

        return new SegmentCommitInfo(
            si,
            seg.delCount,
            0,
            -1, -1, -1,
            StringHelper.randomId()
        );
    }

    public static final class SimDirectory extends Directory {

        private final ConcurrentHashMap<String, Long> fileSizes = new ConcurrentHashMap<>();

        public void registerSegmentSize(String filename, long sizeBytes) {
            fileSizes.put(filename, sizeBytes);
        }

        public void deregisterSegment(String segmentName) {
            fileSizes.remove(segmentName + ".cfs");
        }

        @Override
        public long fileLength(String name) throws IOException {
            return fileSizes.getOrDefault(name, 0L);
        }

        @Override public String[] listAll() throws IOException { return fileSizes.keySet().toArray(new String[0]); }
        @Override public void deleteFile(String name) throws IOException { fileSizes.remove(name); }
        @Override public IndexOutput createOutput(String name, IOContext ctx) throws IOException {
            throw new UnsupportedOperationException("SimDirectory is write-once stub");
        }
        @Override public IndexOutput createTempOutput(String p, String s, IOContext ctx) throws IOException {
            throw new UnsupportedOperationException();
        }
        @Override public void sync(Collection<String> names) throws IOException {}
        @Override public void rename(String source, String dest) throws IOException {}
        @Override public void syncMetaData() throws IOException {}
        @Override public IndexInput openInput(String name, IOContext ctx) throws IOException {
            throw new UnsupportedOperationException("SimDirectory is read stub");
        }
        @Override public Lock obtainLock(String name) throws IOException {
            return NoLockFactory.INSTANCE.obtainLock(this, name);
        }
        @Override public void close() throws IOException {}
        @Override public Set<String> getPendingDeletions() throws IOException { return Collections.emptySet(); }
    }
}
