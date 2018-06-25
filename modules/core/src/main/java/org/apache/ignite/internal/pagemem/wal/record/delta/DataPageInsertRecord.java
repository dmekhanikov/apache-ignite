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
import org.apache.ignite.internal.pagemem.wal.record.WalEncryptedRecord;
import org.apache.ignite.internal.processors.cache.persistence.tree.io.AbstractDataPageIO;
import org.apache.ignite.internal.processors.cache.persistence.tree.io.PageIO;
import org.apache.ignite.internal.util.typedef.internal.S;

/**
 * Insert into data page.
 */
public class DataPageInsertRecord extends PageDeltaRecord implements WalEncryptedRecord {
    /** */
    private byte[] payload;

    /** If {@code true} this record should be encrypted. */
    private final boolean needEncryption;

    /**
     * @param grpId Cache group ID.
     * @param pageId Page ID.
     * @param payload Remainder of the record.
     * @param needEncryption If {@code true} this record should be encrypted.
     */
    public DataPageInsertRecord(
        int grpId,
        long pageId,
        byte[] payload,
        boolean needEncryption
    ) {
        super(grpId, pageId);

        this.payload = payload;
        this.needEncryption = needEncryption;
    }

    /**
     * @return Insert record payload.
     */
    public byte[] payload() {
        return payload;
    }

    /** {@inheritDoc} */
    @Override public void applyDelta(PageMemory pageMem, long pageAddr) throws IgniteCheckedException {
        assert payload != null;

        AbstractDataPageIO io = PageIO.getPageIO(pageAddr);

        io.addRow(pageAddr, payload, pageMem.pageSize());
    }

    /** {@inheritDoc} */
    @Override public RecordType type() {
        return RecordType.DATA_PAGE_INSERT_RECORD;
    }

    /** {@inheritDoc} */
    @Override public boolean needEncryption() {
        return needEncryption;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(DataPageInsertRecord.class, this, "super", super.toString());
    }
}
