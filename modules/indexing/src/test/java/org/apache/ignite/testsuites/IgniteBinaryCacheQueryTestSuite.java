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

package org.apache.ignite.testsuites;

import junit.framework.TestSuite;
import org.apache.ignite.internal.processors.cache.BigEntryQueryTest;
import org.apache.ignite.internal.processors.cache.BinaryMetadataConcurrentUpdateWithIndexesTest;
import org.apache.ignite.internal.processors.cache.BinarySerializationQuerySelfTest;
import org.apache.ignite.internal.processors.cache.BinarySerializationQueryWithReflectiveSerializerSelfTest;
import org.apache.ignite.internal.processors.cache.IgniteCacheBinaryObjectsScanSelfTest;
import org.apache.ignite.internal.processors.cache.IgniteCacheBinaryObjectsScanWithEventsSelfTest;
import org.apache.ignite.internal.processors.cache.IgniteCacheSqlDmlErrorSelfTest;
import org.apache.ignite.internal.processors.cache.IgniteCacheSqlInsertValidationSelfTest;
import org.apache.ignite.internal.processors.cache.index.StopNodeOnRebuildIndexFailureTest;
import org.apache.ignite.internal.processors.query.IgniteSqlCustomSchemaTest;
import org.apache.ignite.internal.processors.query.IgniteSqlCustomSchemaWithPdsEnabled;
import org.apache.ignite.internal.processors.query.IgniteSqlDefaultSchemaTest;
import org.apache.ignite.internal.processors.query.IgniteSqlSchemaNameValidationTest;
import org.apache.ignite.internal.processors.query.IgniteSqlSchemasDiffConfigurationsTest;
import org.apache.ignite.internal.processors.query.JdbcSqlCustomSchemaTest;
import org.apache.ignite.internal.processors.query.JdbcSqlDefaultSchemaTest;
import org.apache.ignite.internal.processors.query.RunningQueriesTest;
import org.apache.ignite.internal.processors.query.h2.GridIndexRebuildTest;

/**
 * Cache query suite with binary marshaller.
 */
public class IgniteBinaryCacheQueryTestSuite extends TestSuite {
    /**
     * @return Suite.
     * @throws Exception In case of error.
     */
    public static TestSuite suite() throws Exception {
        TestSuite suite = IgniteCacheQuerySelfTestSuite.suite();

        // Serialization.
        suite.addTestSuite(BinarySerializationQuerySelfTest.class);
        suite.addTestSuite(BinarySerializationQueryWithReflectiveSerializerSelfTest.class);
        suite.addTestSuite(IgniteCacheBinaryObjectsScanSelfTest.class);
        suite.addTestSuite(IgniteCacheBinaryObjectsScanWithEventsSelfTest.class);
        suite.addTestSuite(BigEntryQueryTest.class);
        suite.addTestSuite(RunningQueriesTest.class);
        suite.addTestSuite(GridIndexRebuildTest.class);

        suite.addTestSuite(BinaryMetadataConcurrentUpdateWithIndexesTest.class);

        suite.addTestSuite(IgniteCacheSqlInsertValidationSelfTest.class);
        suite.addTestSuite(IgniteCacheSqlDmlErrorSelfTest.class);
        suite.addTestSuite(StopNodeOnRebuildIndexFailureTest.class);

        suite.addTestSuite(IgniteSqlDefaultSchemaTest.class);
        suite.addTestSuite(IgniteSqlCustomSchemaTest.class);
        suite.addTestSuite(JdbcSqlDefaultSchemaTest.class);
        suite.addTestSuite(JdbcSqlCustomSchemaTest.class);
        suite.addTestSuite(IgniteSqlSchemaNameValidationTest.class);
        suite.addTestSuite(IgniteSqlCustomSchemaWithPdsEnabled.class);
        suite.addTestSuite(IgniteSqlSchemasDiffConfigurationsTest.class);


        //Should be adjusted. Not ready to be used with BinaryMarshaller.
        //suite.addTestSuite(GridCacheBinarySwapScanQuerySelfTest.class);

        //TODO: the following tests= was never tested with binary. Exclude or pass?
//        suite.addTestSuite(IgniteSqlSchemaIndexingTest.class);

        return suite;
    }
}
