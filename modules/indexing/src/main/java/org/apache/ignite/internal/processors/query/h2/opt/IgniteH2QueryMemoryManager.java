/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.internal.processors.query.h2.opt;

import org.apache.ignite.internal.mem.IgniteOutOfMemoryException;

/**
 * Ignite query memory manager.
 */
public class IgniteH2QueryMemoryManager {
    /** Max memory used to query execute on node. */
    private long maxMem;

    /** Allocated memory size. */
    private long allocated;

    /**
     * @param maxMemory Max allocated m
     */
    public IgniteH2QueryMemoryManager(long maxMemory) {
        maxMem = maxMemory;
    }

    /**
     * Check allocated size is less than query memory pool threshold.
     * @param size Allocated size.
     */
    public void allocate(long size) {
        allocated += size;

        if (allocated >= maxMem)
            throw new IgniteOutOfMemoryException("SQL query out of memory");
    }

    /**
     * Free allocated memory.
     * @param size Free size.
     */
    public void free(long size) {
        assert allocated >= size: "Invalid free memory size [allocated=" + allocated + ", free=" + size + ']';

        allocated -= size;
    }
}
