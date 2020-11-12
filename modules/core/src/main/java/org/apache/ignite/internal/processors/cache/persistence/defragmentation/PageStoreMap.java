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

package org.apache.ignite.internal.processors.cache.persistence.defragmentation;

import java.util.Arrays;
import java.util.Collection;
import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.internal.pagemem.store.PageStore;
import org.apache.ignite.internal.pagemem.store.PageStoreCollection;
import org.apache.ignite.internal.util.collection.IntMap;
import org.apache.ignite.internal.util.collection.IntRWHashMap;
import org.apache.ignite.internal.util.typedef.internal.S;

/** */
class PageStoreMap implements PageStoreCollection {
    /** GroupId -> PartId -> PageStore */
    private final IntMap<IntMap<PageStore>> grpPageStoresMap = new IntRWHashMap<>();

    /** */
    public void addPageStore(
        int grpId,
        int partId,
        PageStore pageStore
    ) {
        System.out.println(S.toString("Adding page store.",
            "grpId", grpId, false,
            "partId", partId, false,
            "this", hashCode(), false
        ));

        IntMap<PageStore> pageStoresMap = grpPageStoresMap.get(grpId);

        //This code cannot be used concurrently. If we decide to parallel defragmentation then we should correct current class.
        if (pageStoresMap == null)
            grpPageStoresMap.put(grpId, pageStoresMap = new IntRWHashMap<>());

        pageStoresMap.put(partId, pageStore);
    }

    /** */
    public void removePageStore(
        int grpId,
        int partId
    ) {
        System.out.println(S.toString("Removing page store.",
            "grpId", grpId, false,
            "partId", partId, false,
            "this", hashCode(), false
        ));

        IntMap<PageStore> pageStoresMap = grpPageStoresMap.get(grpId);

        if (pageStoresMap != null)
            pageStoresMap.remove(partId);
    }

    /** */
    public void clear(int grpId) {
        grpPageStoresMap.remove(grpId);
    }

    /** {@inheritDoc} */
    @Override public PageStore getStore(int grpId, int partId) throws IgniteCheckedException {
        IntMap<PageStore> partPageStoresMap = grpPageStoresMap.get(grpId);

        if (partPageStoresMap == null) {
            throw new IgniteCheckedException(S.toString("Page store map not found. ",
                "grpId", grpId, false,
                "partId", partId, false,
                "keys", Arrays.toString(grpPageStoresMap.keys()), false,
                "this", hashCode(), false
            ));
        }

        PageStore pageStore = partPageStoresMap.get(partId);

        if (pageStore == null) {
            throw new IgniteCheckedException(S.toString("Page store not found. ",
                "grpId", grpId, false,
                "partId", partId, false,
                "keys", Arrays.toString(partPageStoresMap.keys()), false,
                "this", hashCode(), false
            ));
        }

        return pageStore;
    }

    /** {@inheritDoc} */
    @Override public Collection<PageStore> getStores(int grpId) throws IgniteCheckedException {
        IntMap<PageStore> partPageStoresMap = grpPageStoresMap.get(grpId);

        if (partPageStoresMap == null) {
            throw new IgniteCheckedException(S.toString("Page store map not found. ",
                "grpId", grpId, false,
                "keys", Arrays.toString(grpPageStoresMap.keys()), false,
                "this", hashCode(), false
            ));
        }

        return Arrays.asList(partPageStoresMap.values());
    }
}
