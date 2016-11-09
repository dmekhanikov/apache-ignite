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

package org.apache.ignite.internal.processors.platform.entityframework;

import java.util.Arrays;

/**
 * EntityFramework cache key: query + versions.
 */
@SuppressWarnings("WeakerAccess")
public class PlatformDotNetEntityFrameworkCacheKey {
    /** Query text. */
    private final String query;

    /** Entity set versions. */
    private final long[] versions;

    /**
     * Ctor.
     */
    public PlatformDotNetEntityFrameworkCacheKey() {
        query = null;
        versions = null;
    }

    /**
     * Ctor.
     *
     * @param query Query text.
     * @param versions Versions.
     */
    public PlatformDotNetEntityFrameworkCacheKey(String query, long[] versions) {
        assert query != null;

        this.query = query;
        this.versions = versions;
    }

    /**
     * Gets the query text.
     *
     * @return Query text.
     */
    public String query() {
        return query;
    }

    /**
     * Gets the entity set versions.
     *
     * @return Entity set versions.
     */
    public long[] versions() {
        return versions;
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;

        if (o == null || getClass() != o.getClass())
            return false;

        PlatformDotNetEntityFrameworkCacheKey key = (PlatformDotNetEntityFrameworkCacheKey)o;

        //noinspection SimplifiableIfStatement
        if (!query.equals(key.query))
            return false;

        return Arrays.equals(versions, key.versions);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        int result = query.hashCode();

        result = 31 * result + Arrays.hashCode(versions);

        return result;
    }
}
