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
 *
 */

package org.apache.ignite.internal.processors.query.calcite.integration;

import java.math.BigInteger;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.cache.QueryEntity;
import org.apache.ignite.cache.query.annotations.QuerySqlField;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.internal.IgniteEx;
import org.apache.ignite.internal.processors.query.QueryEngine;
import org.apache.ignite.internal.processors.query.calcite.CalciteQueryProcessor;
import org.apache.ignite.internal.processors.query.calcite.QueryChecker;
import org.apache.ignite.internal.processors.query.calcite.util.Commons;
import org.apache.ignite.internal.processors.query.h2.IgniteH2Indexing;
import org.apache.ignite.internal.processors.query.stat.IgniteStatisticsManager;
import org.apache.ignite.internal.processors.query.stat.StatisticsKey;
import org.apache.ignite.internal.processors.query.stat.StatisticsTarget;
import org.apache.ignite.internal.processors.query.stat.config.StatisticsObjectConfiguration;
import org.apache.ignite.internal.util.typedef.F;
import org.apache.ignite.testframework.GridTestUtils;
import org.junit.Test;

/**
 * Tests for server side statistics usage.
 */
public class ServerStatisticsIntegrationTest extends AbstractBasicIntegrationTest {
    /** Server instance. */
    private IgniteEx srv;

    /** All types table row count. */
    private static final int ROW_COUNT = 100;

    /** All types table nullable fields. */
    private static final String[] NULLABLE_FIELDS = {
        "boolean_obj_field",
        "short_obj_field",
        "integer_field",
        "long_obj_field",
        "float_obj_field",
        "double_obj_field",
    };

    /** All types table non nullable fields. */
    private static final String[] NON_NULLABLE_FIELDS = {
        "short_field",
        "int_field",
        "long_field",
        "float_field",
        "double_field"
    };

    /** All types table numeric fields. */
    private static final String[] NUMERIC_FIELDS = {
        "short_obj_field",
        "integer_field",
        "long_obj_field",
        "float_obj_field",
        "double_obj_field",
        "short_field",
        "int_field",
        "long_field",
        "float_field",
        "double_field"
    };

    /** {@inheritDoc} */
    @Override protected void beforeTestsStarted() throws Exception {
        super.beforeTestsStarted();

        createAndPopulateAllTypesTable(0, ROW_COUNT);
    }

    /** {@inheritDoc} */
    @Override protected int nodeCount() {
        return 1;
    }

    /** {@inheritDoc} */
    @Override protected void afterTest() {
        cleanQueryPlanCache();
    }

    /**
     * Run select and check that cost take statisitcs in account:
     * 1) without statistics;
     * 2) with statistics;
     * 3) after deleting statistics.
     */
    @Test
    public void testQueryCostWithStatistics() throws IgniteCheckedException {
        createAndPopulateTable();
        StatisticsKey key = new StatisticsKey("PUBLIC", "PERSON");
        srv = ignite(0);

        TestCost costWoStats = new TestCost(1000., 1000., null, null, null);

        assertQuerySrv("select count(name) from person").matches(QueryChecker.containsCost(costWoStats)).check();

        clearQryCache(srv);

        collectStatistics(srv, key);

        TestCost costWithStats = new TestCost(5., 5., null, null, null);

        assertQuerySrv("select count(name) from person").matches(QueryChecker.containsCost(costWithStats)).check();

        statMgr(srv).dropStatistics(new StatisticsTarget(key));
        clearQryCache(srv);

        assertQuerySrv("select count(name) from person").matches(QueryChecker.containsCost(costWoStats)).check();
    }

    /**
     * Check is null conditions.
     */
    @Test
    public void testNullableFields() throws IgniteCheckedException {
        StatisticsKey key = new StatisticsKey("PUBLIC", "ALL_TYPES");
        srv = ignite(0);

        collectStatistics(srv, key);

        String sql = "select * from all_types ";

        for (String nullableField : NULLABLE_FIELDS) {
            assertQuerySrv(sql + "where " + nullableField + " is null")
                .matches(QueryChecker.containsRowCount(25.)).check();

            assertQuerySrv(sql + "where " + nullableField + " is not null")
                .matches(QueryChecker.containsRowCount(75.)).check();
        }

        for (String nullableField : NULLABLE_FIELDS) {
            assertQuerySrv(sql + "where " + nullableField + " is null")
                .matches(QueryChecker.containsRowCount(25.)).check();

            assertQuerySrv(sql + "where " + nullableField + " is not null")
                .matches(QueryChecker.containsRowCount(75.)).check();
        }
    }

    /**
     * Test multiple condition for the same query.
     * @throws IgniteCheckedException
     */
    @Test
    public void testMultipleConditionQuery() throws IgniteCheckedException {
        StatisticsKey key = new StatisticsKey("PUBLIC", "ALL_TYPES");
        srv = ignite(0);

        collectStatistics(srv, key);

        Set<String> nonNullableFields = new HashSet(Arrays.asList(NON_NULLABLE_FIELDS));

        for (String numericField : NUMERIC_FIELDS) {
            double allRowCnt = (nonNullableFields.contains(numericField)) ? (double)ROW_COUNT : 0.75 * ROW_COUNT;

            String fieldSql = String.format("select * from all_types where %s > -100 and %s > 0", numericField, numericField);

            assertQuerySrv(fieldSql).matches(QueryChecker.containsRowCount(allRowCnt)).check();

            fieldSql = String.format("select * from all_types where %s < 1000 and %s < 101", numericField, numericField);

            assertQuerySrv(fieldSql).matches(QueryChecker.containsRowCount(allRowCnt)).check();

            fieldSql = String.format("select * from all_types where %s > -100 and %s < 1000", numericField, numericField);

            assertQuerySrv(fieldSql).matches(QueryChecker.containsRowCount(allRowCnt)).check();
        }
    }

    /**
     * Check range condition with not null conditions.
     *
     * @throws IgniteCheckedException In case of error.
     */
    @Test
    public void testNonNullMultipleConditionQuery() throws IgniteCheckedException {
        StatisticsKey key = new StatisticsKey("PUBLIC", "ALL_TYPES");
        srv = ignite(0);

        collectStatistics(srv, key);

        Set<String> nonNullableFields = new HashSet(Arrays.asList(NON_NULLABLE_FIELDS));

        // time
        String timeSql = "select * from all_types where time_field is not null";

        assertQuerySrv(timeSql).matches(QueryChecker.containsRowCount(ROW_COUNT * 0.75)).check();

        timeSql += " and time_field > '00:00:00'";

        assertQuerySrv(timeSql).matches(QueryChecker.containsRowCount(ROW_COUNT * 0.75)).check();

        // date
        String dateSql = "select * from all_types where date_field is not null";

        assertQuerySrv(dateSql).matches(QueryChecker.containsRowCount(ROW_COUNT * 0.75)).check();

        dateSql += " and date_field > '1000-01-01'";

        assertQuerySrv(dateSql).matches(QueryChecker.containsRowCount(ROW_COUNT * 0.75)).check();

        // timestamp
        String timestampSql = "select * from all_types where timestamp_field is not null ";

        assertQuerySrv(timestampSql).matches(QueryChecker.containsRowCount(ROW_COUNT * 0.75)).check();

        timestampSql += " and timestamp_field > '1000-01-10 11:59:59'";

        assertQuerySrv(timestampSql).matches(QueryChecker.containsRowCount(ROW_COUNT * 0.75)).check();

        // numeric fields
        for (String numericField : NUMERIC_FIELDS) {
            double allRowCnt = (nonNullableFields.contains(numericField)) ? (double)ROW_COUNT : 0.75 * ROW_COUNT;

            String fieldSql = String.format("select * from all_types where %s is not null",
                numericField, numericField);

            assertQuerySrv(fieldSql).matches(QueryChecker.containsRowCount(allRowCnt)).check();

            fieldSql = String.format("select * from all_types where %s is not null and %s > 0",
                numericField, numericField);

            assertQuerySrv(fieldSql).matches(QueryChecker.containsRowCount(allRowCnt)).check();

        }
    }

    /**
     * Check condition with projections:
     *
     * 1) Condition on the one of fields in select list.
     * 2) Confition on the field not from select list.
     */
    @Test
    public void testProjections() throws IgniteCheckedException {
        StatisticsKey key = new StatisticsKey("PUBLIC", "ALL_TYPES");
        srv = ignite(0);

        collectStatistics(srv, key);

        String sql = "select %s, %s from all_types where %s < " + ROW_COUNT;

        String sql2 = "select %s from all_types where %s >= " + (-ROW_COUNT);

        Set<String> nonNullableFields = new HashSet(Arrays.asList(NON_NULLABLE_FIELDS));

        for (int firstFieldIdx = 0; firstFieldIdx < NUMERIC_FIELDS.length - 1; firstFieldIdx++) {
            String firstField = NUMERIC_FIELDS[firstFieldIdx];
            double firstAllRowCnt = (nonNullableFields.contains(firstField)) ? (double)ROW_COUNT : 0.75 * ROW_COUNT;

            for (int secFieldIdx = firstFieldIdx + 1; secFieldIdx < NUMERIC_FIELDS.length; secFieldIdx++) {
                String secField = NUMERIC_FIELDS[secFieldIdx];

                double secAllRowCnt = (nonNullableFields.contains(secField)) ? (double)ROW_COUNT : 0.75 * ROW_COUNT;

                String qry = String.format(sql, firstField, secField, secField);

                assertQuerySrv(qry).matches(QueryChecker.containsRowCount(secAllRowCnt)).check();

                qry = String.format(sql, firstField, secField, firstField);

                assertQuerySrv(qry).matches(QueryChecker.containsRowCount(firstAllRowCnt)).check();

                qry = String.format(sql2, firstField, secField);

                assertQuerySrv(qry).matches(QueryChecker.containsRowCount(secAllRowCnt)).check();

                qry = String.format(sql2, secField, firstField);

                assertQuerySrv(qry).matches(QueryChecker.containsRowCount(firstAllRowCnt)).check();
            }
        }
    }

    /**
     * Check randge with min/max borders.
     */
    @Test
    public void testBorders() throws IgniteCheckedException {
        StatisticsKey key = new StatisticsKey("PUBLIC", "ALL_TYPES");
        srv = ignite(0);

        collectStatistics(srv, key);

        // time
        String timeSql = "select * from all_types where time_field > '00:00:00'";

        assertQuerySrv(timeSql).matches(QueryChecker.containsRowCount(ROW_COUNT * 0.75)).check();

        // date
        String dateSql = "select * from all_types where date_field > '1000-01-10'";

        assertQuerySrv(dateSql).matches(QueryChecker.containsRowCount(ROW_COUNT * 0.75)).check();

        // timestamp
        String timestampSql = "select * from all_types where timestamp_field > '1000-01-10 11:59:59'";

        assertQuerySrv(timestampSql).matches(QueryChecker.containsRowCount(ROW_COUNT * 0.75)).check();

        String sql = "select * from all_types ";

        Set<String> nonNullableFields = new HashSet(Arrays.asList(NON_NULLABLE_FIELDS));
        for (String numericField : NUMERIC_FIELDS) {
            double allRowCnt = (nonNullableFields.contains(numericField)) ? (double)ROW_COUNT : 0.75 * ROW_COUNT;

            String fieldSql = sql + "where " + numericField;

            assertQuerySrv(fieldSql + " <  -1").matches(QueryChecker.containsRowCount(1.)).check();
            assertQuerySrv(fieldSql + " <  0").matches(QueryChecker.containsRowCount(1.)).check();
            assertQuerySrv(fieldSql + " <=  0").matches(QueryChecker.containsRowCount(1.)).check();
            assertQuerySrv(fieldSql + " >=  0").matches(QueryChecker.containsRowCount(allRowCnt)).check();
            assertQuerySrv(fieldSql + " > 0").matches(QueryChecker.containsRowCount(allRowCnt)).check();

            assertQuerySrv(fieldSql + " > 101").matches(QueryChecker.containsRowCount(1.)).check();
            assertQuerySrv(fieldSql + " > 100").matches(QueryChecker.containsRowCount(1.)).check();
            assertQuerySrv(fieldSql + " >= 100").matches(QueryChecker.containsRowCount(1.)).check();
            assertQuerySrv(fieldSql + " <= 100").matches(QueryChecker.containsRowCount(allRowCnt)).check();
            assertQuerySrv(fieldSql + " < 100").matches(QueryChecker.containsRowCount(allRowCnt)).check();
        }
    }

    /**
     * Clear query cache in specified node.
     *
     * @param ign Ignite node to clear calcite query cache on.
     */
    protected void clearQryCache(IgniteEx ign) {
        CalciteQueryProcessor qryProc = (CalciteQueryProcessor)Commons.lookupComponent(
            (ign).context(), QueryEngine.class);

        qryProc.queryPlanCache().clear();
    }

    /**
     * Collect statistics by speicifed key on specified node.
     *
     * @param ign Node to collect statistics on.
     * @param key Statistics key to collect statistics by.
     * @throws IgniteCheckedException In case of errors.
     */
    protected void collectStatistics(IgniteEx ign, StatisticsKey key) throws IgniteCheckedException {
        IgniteStatisticsManager statMgr = statMgr(ign);

        statMgr.collectStatistics(new StatisticsObjectConfiguration(key));

        assertTrue(GridTestUtils.waitForCondition(() -> statMgr.getLocalStatistics(key) != null, 1000));
    }

    /**
     * Get statistics manager.
     *
     * @param ign Node to get statistics manager from.
     * @return IgniteStatisticsManager.
     */
    protected IgniteStatisticsManager statMgr(IgniteEx ign) {
        IgniteH2Indexing indexing = (IgniteH2Indexing)ign.context().query().getIndexing();

        return indexing.statsManager();
    }

    /** */
    protected QueryChecker assertQuerySrv(String qry) {
        return new QueryChecker(qry) {
            @Override protected QueryEngine getEngine() {
                return Commons.lookupComponent(srv.context(), QueryEngine.class);
            }
        };
    }

    /**
     * Create (if not exists) and populate cache with all types.
     *
     * @param start first key idx.
     * @param count rows count.
     * @return Populated cache.
     */
    protected IgniteCache<Integer, AllTypes> createAndPopulateAllTypesTable(int start, int count) {
        IgniteCache<Integer, AllTypes> all_types = grid(0).getOrCreateCache(new CacheConfiguration<Integer, AllTypes>()
            .setName("all_types")
            .setSqlSchema("PUBLIC")
            .setQueryEntities(F.asList(new QueryEntity(Integer.class, AllTypes.class).setTableName("all_types")))
            .setBackups(2)
        );

        for (int i = start; i < start + count; i++) {
            boolean null_values = (i & 3) == 1;

            all_types.put(i, new AllTypes(i, null_values));
        }

        return all_types;
    }

    /**
     * Test cost with nulls for unknown values.
     */
    public static class TestCost {
        /** */
        Double rowCount;

        /** */
        Double cpu;

        /** */
        Double memory;

        /** */
        Double io;

        /** */
        Double network;

        /**
         * @return Row count.
         */
        public Double rowCount() {
            return rowCount;
        }

        /**
         * @return Cpu.
         */
        public Double cpu() {
            return cpu;
        }

        /**
         * @return Memory
         */
        public Double memory() {
            return memory;
        }

        /**
         * @return Io.
         */
        public Double io() {
            return io;
        }

        /**
         * @return Network.
         */
        public Double network() {
            return network;
        }

        /**
         * Constructor.
         *
         * @param rowCount Row count.
         * @param cpu Cpu.
         * @param memory Memory.
         * @param io Io.
         * @param network Network.
         */
        public TestCost(Double rowCount, Double cpu, Double memory, Double io, Double network) {
            this.rowCount = rowCount;
            this.cpu = cpu;
            this.memory = memory;
            this.io = io;
            this.network = network;
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return "TestCost{" +
                "rowCount=" + rowCount +
                ", cpu=" + cpu +
                ", memory=" + memory +
                ", io=" + io +
                ", network=" + network +
                '}';
        }
    }

    /**
     * Test class with fields of all types.
     */
    public static class AllTypes {
        /** */
        @QuerySqlField
        public String string_field;

        /** */
        @QuerySqlField
        public byte[] byte_arr_field;

        /** */
        @QuerySqlField
        public boolean boolean_field;

        /** */
        @QuerySqlField
        public Boolean boolean_obj_field;

        /** */
        @QuerySqlField
        public short short_field;

        /** */
        @QuerySqlField
        public Short short_obj_field;

        /** */
        @QuerySqlField
        public int int_field;

        /** */
        @QuerySqlField
        public Integer integer_field;

        /** */
        @QuerySqlField
        public long long_field;

        /** */
        @QuerySqlField
        public Long long_obj_field;

        /** */
        @QuerySqlField
        public float float_field;

        /** */
        @QuerySqlField
        public Float float_obj_field;

        /** */
        @QuerySqlField
        public double double_field;

        /** */
        @QuerySqlField
        public Double double_obj_field;

        /** */
        @QuerySqlField
        public Date date_field;

        /** */
        @QuerySqlField
        public Time time_field;

        /** */
        @QuerySqlField
        public Timestamp timestamp_field;

        /**
         * Constructor.
         *
         * @param i idx to generate all fields values by.
         * @param null_val Should object fields be equal to {@code null}.
         */
        public AllTypes(int i, boolean null_val) {
            string_field = (null_val) ? null : "string_field_value" + i;
            byte_arr_field = (null_val) ? null : BigInteger.valueOf(i).toByteArray();
            boolean_field = (i & 1) == 0;
            boolean_obj_field = (null_val) ? null : (i & 1) == 0;
            short_field = (short)i;
            short_obj_field = (null_val) ? null : short_field;
            int_field = i;
            integer_field = (null_val) ? null : i;
            long_field = i;
            long_obj_field = (null_val) ? null : long_field;
            float_field = i;
            float_obj_field = (null_val) ? null : float_field;
            double_field = i;
            double_obj_field = (null_val) ? null : double_field;
            date_field = (null_val) ? null : Date.valueOf(String.format("%04d-04-09", 1000 + i));
            time_field = (null_val) ? null : new Time(i * 1000);
            timestamp_field = (null_val) ? null : Timestamp.valueOf(String.format("%04d-04-09 12:00:00", 1000 + i));
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return "AllTypes{" +
                "string_field='" + string_field + '\'' +
                ", byte_arr_field=" + byte_arr_field +
                ", boolean_field=" + boolean_field +
                ", boolean_obj_field=" + boolean_obj_field +
                ", short_field=" + short_field +
                ", short_obj_field=" + short_obj_field +
                ", int_field=" + int_field +
                ", Integer_field=" + integer_field +
                ", long_field=" + long_field +
                ", long_obj_field=" + long_obj_field +
                ", float_field=" + float_field +
                ", float_obj_field=" + float_obj_field +
                ", double_field=" + double_field +
                ", double_obj_field=" + double_obj_field +
                ", date_field=" + date_field +
                ", time_field=" + time_field +
                ", timestamp_field=" + timestamp_field +
                '}';
        }
    }
}
