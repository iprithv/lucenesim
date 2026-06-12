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

import java.util.List;

/**
 * Synchronous merge scheduler - merges complete instantly in the same "thread".
 *
 * <p>This models {@link org.apache.lucene.index.SerialMergeScheduler}.
 */
public class SerialSimScheduler implements SimScheduler {

    @Override
    public void reset() {
        // Stateless - nothing to reset.
    }

    @Override
    public void scheduleMerge(List<SimSegment> inputs, SimSegment output, MergeCallback callback) {
        callback.onComplete(output);
    }

    @Override
    public boolean advanceToTick(long tick) {
        // Synchronous merges complete immediately; nothing to do here.
        return false;
    }

    @Override
    public void drain() {
        // All merges already completed synchronously.
    }

    @Override
    public boolean isSynchronous() {
        return true;
    }
}
