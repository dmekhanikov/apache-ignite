/*
 * Copyright 2019 GridGain Systems, Inc. and Contributors.
 *
 * Licensed under the GridGain Community Edition License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.gridgain.com/products/software/community-edition/gridgain-community-edition-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ignite.internal.pagemem.wal.record;

import org.apache.ignite.internal.util.typedef.internal.S;

public class BrokenRecord extends WALRecord {
    private final WALRecord innerRecord;
    private final Exception exception;

    public BrokenRecord(WALRecord record, Exception exception) {
        innerRecord = record;
        this.exception = exception;
    }

    @Override public RecordType type() {
        return innerRecord.type();
    }

    @Override public String toString() {
        String e = exception == null ? "unknown" : exception.getMessage();

        return "[ERROR] BROKEN_RECORD (Exception: " + e + ")" +
            S.toString(BrokenRecord.class, this);
    }
}
