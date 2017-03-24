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
package org.apache.ignite.internal.processors.cache.database.evict;

import org.apache.ignite.IgniteCheckedException;

/**
 * Entry point for per-page eviction. Accepts information about touching data pages,
 * capable of evicting "the least needed" page (according to implemented eviction algorithm).
 */
public interface PageEvictionTracker {
    /**
     * Call this method when data page is accessed.
     *
     * @param pageId Page id.
     */
    public void touchPage(long pageId) throws IgniteCheckedException;

    /**
     * Evicts one data page.
     * In most cases, all entries will be removed from the page.
     * Method guarantees removing at least one entry from "evicted" data page. Removing all entries may be
     * not possible, as some of them can be used by active transactions.
     */
    public void evictDataPage() throws IgniteCheckedException;

    /**
     * Call this method when last entry is removed from data page.
     *
     * @param pageId Page id.
     */
    public void forgetPage(long pageId) throws IgniteCheckedException;
}
