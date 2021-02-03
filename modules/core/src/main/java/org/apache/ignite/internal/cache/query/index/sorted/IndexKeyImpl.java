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

package org.apache.ignite.internal.cache.query.index.sorted;

import java.util.Arrays;
import org.apache.ignite.cache.query.index.sorted.IndexKey;

/**
 * Complex index key that represents a user index query.
 */
public class IndexKeyImpl implements IndexKey {
    /** Underlying keys set from user query. */
    private final Object[] keys;

    /** */
    public IndexKeyImpl(Object[] keys) {
        this.keys = keys;
    }

    /** {@inheritDoc} */
    @Override public Object getKey(int idx) {
        return keys[idx];
    }

    /** {@inheritDoc} */
    @Override public Object[] getKeys() {
        return keys;
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;

        if (o == null || getClass() != o.getClass())
            return false;

        IndexKeyImpl key = (IndexKeyImpl) o;

        return Arrays.equals(keys, key.keys);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return Arrays.hashCode(keys);
    }
}
