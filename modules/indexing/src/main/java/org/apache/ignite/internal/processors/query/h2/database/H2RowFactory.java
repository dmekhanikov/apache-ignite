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

package org.apache.ignite.internal.processors.query.h2.database;

import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.internal.pagemem.PageIdUtils;
import org.apache.ignite.internal.processors.cache.GridCacheContext;
import org.apache.ignite.internal.processors.cache.persistence.CacheDataRowAdapter;
import org.apache.ignite.internal.processors.cache.tree.mvcc.data.MvccDataRow;
import org.apache.ignite.internal.processors.query.h2.opt.H2SearchRow;
import org.apache.ignite.internal.processors.query.h2.opt.H2UpdateRowAdapter;
import org.apache.ignite.internal.processors.query.h2.opt.GridH2RowDescriptor;

/**
 * Data store for H2 rows.
 */
public class H2RowFactory {
    /** */
    private final GridCacheContext<?,?> cctx;

    /** */
    private final GridH2RowDescriptor rowDesc;

    /**
     * @param rowDesc Row descriptor.
     * @param cctx Cache context.
     */
    public H2RowFactory(GridH2RowDescriptor rowDesc, GridCacheContext<?,?> cctx) {
        this.rowDesc = rowDesc;
        this.cctx = cctx;
    }

    /**
     * !!! This method must be invoked in read or write lock of referring index page. It is needed to
     * !!! make sure that row at this link will be invisible, when the link will be removed from
     * !!! from all the index pages, so that row can be safely erased from the data page.
     *
     * @param link Link.
     * @return Row.
     * @throws IgniteCheckedException If failed.
     */
    public H2SearchRow getRow(long link) throws IgniteCheckedException {
        CacheDataRowAdapter row = new CacheDataRowAdapter(link);

        row.initFromLink(
            cctx.group(),
            CacheDataRowAdapter.RowData.FULL,
            true
        );

        return rowDesc.createRow(row);
    }

    /**
     * @param link Link.
     * @param mvccCrdVer Mvcc coordinator version.
     * @param mvccCntr Mvcc counter.
     * @param mvccOpCntr Mvcc operation counter.
     * @return Row.
     */
    public H2SearchRow getMvccRow(long link, long mvccCrdVer, long mvccCntr, int mvccOpCntr) {
        int partId = PageIdUtils.partId(PageIdUtils.pageId(link));

        MvccDataRow row = new MvccDataRow(
            cctx.group(),
            0,
            link,
            partId,
            null,
            mvccCrdVer,
            mvccCntr,
            mvccOpCntr,
            true
        );

        return rowDesc.createRow(row);
    }

    public H2UpdateRowAdapter getRowForUpdate(long link) throws IgniteCheckedException {
        CacheDataRowAdapter row = new CacheDataRowAdapter(link);

        row.initFromLink(
            cctx.group(),
            CacheDataRowAdapter.RowData.FULL,
            true
        );

        return rowDesc.createRowForUpdate(row);
    }

    public H2UpdateRowAdapter getMvccRowForUpdate(long link, long mvccCrdVer, long mvccCntr, int mvccOpCntr)
        throws IgniteCheckedException {
        int partId = PageIdUtils.partId(PageIdUtils.pageId(link));

        MvccDataRow row = new MvccDataRow(
            cctx.group(),
            0,
            link,
            partId,
            null,
            mvccCrdVer,
            mvccCntr,
            mvccOpCntr,
            true
        );

        return rowDesc.createRowForUpdate(row);
    }
}
