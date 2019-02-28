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

package org.apache.ignite.internal.processors.odbc.jdbc;

import java.util.List;
import org.apache.ignite.binary.BinaryObjectException;
import org.apache.ignite.internal.binary.BinaryReaderExImpl;
import org.apache.ignite.internal.binary.BinaryWriterExImpl;
import org.apache.ignite.internal.processors.odbc.ClientListenerProtocolVersion;
import org.apache.ignite.internal.sql.optimizer.affinity.PartitionResult;
import org.apache.ignite.internal.util.typedef.internal.S;

/**
 * JDBC query execute result.
 */
public class JdbcQueryExecuteResult extends JdbcResult {
    /** Cursor ID. */
    private long cursorId;

    /** Query result rows. */
    private List<List<Object>> items;

    /** Flag indicating the query has no unfetched results. */
    private boolean last;

    /** Flag indicating the query is SELECT query. {@code false} for DML/DDL queries. */
    private boolean isQuery;

    /** Update count. */
    private long updateCnt;

    // TODO: 28.02.19 not sure that partRes should be only in JdbcQueryExecuteResult.
    /** Partition result. */
    private PartitionResult partRes;

    /**
     * Constructor.
     */
    JdbcQueryExecuteResult() {
        super(QRY_EXEC);
    }

    /**
     * @param cursorId Cursor ID.
     * @param items Query result rows.
     * @param last Flag indicates the query has no unfetched results.
     * @param partRes partition result to use for best affort affinity on the client side.
     */
    JdbcQueryExecuteResult(long cursorId, List<List<Object>> items, boolean last, PartitionResult partRes) {
        super(QRY_EXEC);

        this.cursorId = cursorId;
        this.items = items;
        this.last = last;
        isQuery = true;
        this.partRes = partRes;
    }

    /**
     * @param cursorId Cursor ID.
     * @param updateCnt Update count for DML queries.
     */
    public JdbcQueryExecuteResult(long cursorId, long updateCnt) {
        super(QRY_EXEC);

        this.cursorId = cursorId;
        this.last = true;
        this.isQuery = false;
        this.updateCnt = updateCnt;
    }

    /**
     * @return Cursor ID.
     */
    public long cursorId() {
        return cursorId;
    }

    /**
     * @return Query result rows.
     */
    public List<List<Object>> items() {
        return items;
    }

    /**
     * @return Flag indicating the query has no unfetched results.
     */
    public boolean last() {
        return last;
    }

    /**
     * @return Flag indicating the query is SELECT query. {@code false} for DML/DDL queries.
     */
    public boolean isQuery() {
        return isQuery;
    }

    /**
     * @return Update count for DML queries.
     */
    public long updateCount() {
        return updateCnt;
    }

    /** {@inheritDoc} */
    @Override public void writeBinary(BinaryWriterExImpl writer,
        ClientListenerProtocolVersion ver) throws BinaryObjectException {
        super.writeBinary(writer, ver);

        writer.writeLong(cursorId);
        writer.writeBoolean(isQuery);

        if (isQuery) {
            assert items != null;

            writer.writeBoolean(last);

            JdbcUtils.writeItems(writer, items);
        }
        else
            writer.writeLong(updateCnt);

        writer.writeBoolean(partRes != null);

        if (partRes != null)
            partRes.writeBinary(writer, ver);
    }

    /** {@inheritDoc} */
    @Override public void readBinary(BinaryReaderExImpl reader,
        ClientListenerProtocolVersion ver) throws BinaryObjectException {
        super.readBinary(reader, ver);

        cursorId = reader.readLong();
        isQuery = reader.readBoolean();

        if (isQuery) {
            last = reader.readBoolean();

            items = JdbcUtils.readItems(reader);
        }
        else {
            last = true;

            updateCnt = reader.readLong();
        }

        if (reader.readBoolean())
            partRes = PartitionResult.readResult(reader, ver);
    }

    /**
     * @return Partition result.
     */
    public PartitionResult partitionResult() {
        return partRes;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(JdbcQueryExecuteResult.class, this);
    }
}
