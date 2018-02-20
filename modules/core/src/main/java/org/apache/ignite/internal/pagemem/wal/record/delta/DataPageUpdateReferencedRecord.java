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

package org.apache.ignite.internal.pagemem.wal.record.delta;

import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.internal.pagemem.PageMemory;
import org.apache.ignite.internal.pagemem.wal.WALPointer;
import org.apache.ignite.internal.pagemem.wal.record.DataRecord;
import org.apache.ignite.internal.pagemem.wal.record.WALReferenceAwareRecord;
import org.apache.ignite.internal.processors.cache.persistence.Storable;
import org.apache.ignite.internal.processors.cache.persistence.tree.io.AbstractDataPageIO;
import org.apache.ignite.internal.processors.cache.persistence.tree.io.PageIO;
import org.apache.ignite.internal.util.typedef.internal.S;

/**
 * Update existing record in data page with referenced payload.
 */
public class DataPageUpdateReferencedRecord extends PageDeltaRecord implements WALReferenceAwareRecord {
    /** */
    private final int itemId;

    /** WAL reference to {@link DataRecord}. */
    private WALPointer reference;

    /** Row associated with the page data. */
    private Storable row;

    /**
     * @param grpId Cache group ID.
     * @param pageId Page ID.
     * @param itemId Item ID.
     * @param reference WAL reference to {@link DataRecord}.
     */
    public DataPageUpdateReferencedRecord(
        int grpId,
        long pageId,
        int itemId,
        WALPointer reference
    ) {
        super(grpId, pageId);

        this.itemId = itemId;
        this.reference = reference;
    }

    /**
     * @return Item ID.
     */
    public int itemId() {
        return itemId;
    }

    /** {@inheritDoc} */
    @Override public void applyDelta(PageMemory pageMem, long pageAddr) throws IgniteCheckedException {
        assert row != null : "Row is not associated with record. Unable to apply it to PageMemory";

        AbstractDataPageIO<Storable> io = PageIO.getPageIO(pageAddr);

        io.updateRow(pageAddr, itemId, pageMem.pageSize(), null, row, io.getRowSize(row));
    }

    /** {@inheritDoc} */
    @Override public RecordType type() {
        return RecordType.DATA_PAGE_UPDATE_REF_RECORD;
    }

    /** {@inheritDoc} */
    @Override public void row(Storable row) {
        this.row = row;
    }

    /** {@inheritDoc} */
    @Override public WALPointer reference() {
        return reference;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(DataPageUpdateReferencedRecord.class, this,
                "reference", reference.toString(),
                "row", row != null ? row.toString() : "null",
                "super", super.toString());
    }
}
