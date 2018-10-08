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

package org.apache.ignite.internal.processors.cache.distributed;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.query.SqlFieldsQuery;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.internal.IgniteEx;
import org.apache.ignite.internal.processors.cache.distributed.CacheBlockOnReadAbstractTest.Params;
import org.apache.ignite.internal.processors.query.schema.message.SchemaOperationStatusMessage;
import org.apache.ignite.internal.util.typedef.T3;
import org.apache.ignite.testframework.junits.common.GridCommonAbstractTest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.apache.ignite.cache.CacheAtomicityMode.ATOMIC;
import static org.apache.ignite.cache.CacheAtomicityMode.TRANSACTIONAL;

/**
 *
 */
public class CacheBlockOnCreateDestoryIndexTest extends GridCommonAbstractTest {
    /** */
    private final List<? extends CacheBlockOnReadAbstractTest> tests = Arrays.asList(
        new CacheBlockOnSingleGetTest() {
            @Nullable @Override protected <A extends Annotation> A currentTestAnnotation(Class<A> annotationCls) {
                return CacheBlockOnCreateDestoryIndexTest.this.currentTestAnnotation(annotationCls);
            }
        },
        new CacheBlockOnGetAllTest() {
            @Nullable @Override protected <A extends Annotation> A currentTestAnnotation(Class<A> annotationCls) {
                return CacheBlockOnCreateDestoryIndexTest.this.currentTestAnnotation(annotationCls);
            }
        },
        new CacheBlockOnScanTest() {
            @Nullable @Override protected <A extends Annotation> A currentTestAnnotation(Class<A> annotationCls) {
                return CacheBlockOnCreateDestoryIndexTest.this.currentTestAnnotation(annotationCls);
            }
        },
        new CacheBlockOnSqlQueryTest() {
            @Nullable @Override protected <A extends Annotation> A currentTestAnnotation(Class<A> annotationCls) {
                return CacheBlockOnCreateDestoryIndexTest.this.currentTestAnnotation(annotationCls);
            }
        }
    );

    /** {@inheritDoc} */
    @Override protected void beforeTest() throws Exception {
        currentTest().beforeTest();
    }

    /** {@inheritDoc} */
    @Override protected void afterTest() throws Exception {
        currentTest().afterTest();
    }

    /**
     * @throws Exception If failed.
     */
    @TestIndex(0)
    @Params(atomicityMode = ATOMIC)
    public void testCreateIndexAtomicGet() throws Exception {
        doTestCreateIndex();
    }

    /**
     * @throws Exception If failed.
     */
    @TestIndex(0)
    @Params(atomicityMode = TRANSACTIONAL)
    public void testCreateIndexTransactionalGet() throws Exception {
        doTestCreateIndex();
    }

    /**
     * @throws Exception If failed.
     */
    @TestIndex(1)
    @Params(atomicityMode = ATOMIC)
    public void testCreateIndexAtomicGetAll() throws Exception {
        doTestCreateIndex();
    }

    /**
     * @throws Exception If failed.
     */
    @TestIndex(1)
    @Params(atomicityMode = TRANSACTIONAL)
    public void testCreateIndexTransactionalGetAll() throws Exception {
        doTestCreateIndex();
    }

    /**
     * @throws Exception If failed.
     */
    @TestIndex(2)
    @Params(atomicityMode = ATOMIC)
    public void testCreateIndexAtomicScan() throws Exception {
        doTestCreateIndex();
    }

    /**
     * @throws Exception If failed.
     */
    @TestIndex(2)
    @Params(atomicityMode = TRANSACTIONAL)
    public void testCreateIndexTransactionalScan() throws Exception {
        doTestCreateIndex();
    }

    /**
     * @throws Exception If failed.
     */
    @TestIndex(3)
    @Params(atomicityMode = ATOMIC)
    public void testCreateIndexAtomicSqlQuery() throws Exception {
        doTestCreateIndex();
    }

    /**
     * @throws Exception If failed.
     */
    @TestIndex(3)
    @Params(atomicityMode = TRANSACTIONAL)
    public void testCreateIndexTransactionalSqlQuery() throws Exception {
        doTestCreateIndex();
    }

    /**
     * @throws Exception If failed.
     */
    @TestIndex(0)
    @Params(atomicityMode = ATOMIC)
    public void testDestroyIndexAtomicGet() throws Exception {
        doTestDestroyIndex();
    }

    /**
     * @throws Exception If failed.
     */
    @TestIndex(0)
    @Params(atomicityMode = TRANSACTIONAL)
    public void testDestroyIndexTransactionalGet() throws Exception {
        doTestDestroyIndex();
    }

    /**
     * @throws Exception If failed.
     */
    @TestIndex(1)
    @Params(atomicityMode = ATOMIC)
    public void testDestroyIndexAtomicGetAll() throws Exception {
        doTestDestroyIndex();
    }

    /**
     * @throws Exception If failed.
     */
    @TestIndex(1)
    @Params(atomicityMode = TRANSACTIONAL)
    public void testDestroyIndexTransactionalGetAll() throws Exception {
        doTestDestroyIndex();
    }

    /**
     * @throws Exception If failed.
     */
    @TestIndex(2)
    @Params(atomicityMode = ATOMIC)
    public void testDestroyIndexAtomicScan() throws Exception {
        doTestDestroyIndex();
    }

    /**
     * @throws Exception If failed.
     */
    @TestIndex(2)
    @Params(atomicityMode = TRANSACTIONAL)
    public void testDestroyIndexTransactionalScan() throws Exception {
        doTestDestroyIndex();
    }

    /**
     * @throws Exception If failed.
     */
    @TestIndex(3)
    @Params(atomicityMode = ATOMIC)
    public void testDestroyIndexAtomicSqlQuery() throws Exception {
        doTestDestroyIndex();
    }

    /**
     * @throws Exception If failed.
     */
    @TestIndex(3)
    @Params(atomicityMode = TRANSACTIONAL)
    public void testDestroyIndexTransactionalSqlQuery() throws Exception {
        doTestDestroyIndex();
    }

    /**
     * @throws Exception If failed.
     */
    private void doTestCreateIndex() throws Exception {
        IgniteEx ignite = currentTest().baseline().get(0);

        List<T3<String, String, String>> caches = createCaches(ignite);

        currentTest().doTest(
            msg -> msg instanceof SchemaOperationStatusMessage,
            () -> createIndex(ignite, caches.remove(caches.size() - 1))
        );
    }

    /**
     * @throws Exception If failed.
     */
    private void doTestDestroyIndex() throws Exception {
        IgniteEx ignite = currentTest().baseline().get(0);

        List<T3<String, String, String>> caches = createCaches(ignite);

        for (T3<String, String, String> pair : caches)
            createIndex(ignite, pair);

        currentTest().doTest(
            msg -> msg instanceof SchemaOperationStatusMessage,
            () -> destroyIndex(ignite, caches.remove(caches.size() - 1))
        );
    }

    /**
     * @param ignite Ignite instance.
     * @return 3 pairs {@code {cacheName, tableName, indexName}} for further sql operations.
     */
    @NotNull private List<T3<String, String, String>> createCaches(IgniteEx ignite) {
        List<T3<String, String, String>> caches = new ArrayList<>();

        for (int i = 0; i < 3; i++) {
            String tblName = "TABLE_" + UUID.randomUUID().toString().replace('-', '_');

            String cacheName = "CACHE_" + tblName;

            CacheConfiguration<?, ?> ccfg = new CacheConfiguration<>(cacheName).setSqlSchema("PUBLIC");

            IgniteCache<?, ?> cache = ignite.createCache(ccfg);

            String createTblQryStr = String.format(
                "CREATE TABLE %s (id LONG, name VARCHAR, city_id LONG, PRIMARY KEY (id, city_id)) " +
                    "WITH \"backups=1, affinityKey=city_id\"",
                tblName
            );

            cache.query(new SqlFieldsQuery(createTblQryStr)).getAll();

            String idxName = "IDX_" + tblName;

            caches.add(new T3<>(cacheName, tblName, idxName));
        }

        return caches;
    }

    /**
     *
     */
    private void createIndex(IgniteEx ignite, T3<String, String, String> pair) {
        IgniteCache<?, ?> cache = ignite.getOrCreateCache(pair.get1());

        String createIdxQryStr = String.format("CREATE INDEX %S on %s (city_id)", pair.get3(), pair.get2());

        cache.query(new SqlFieldsQuery(createIdxQryStr)).getAll();
    }

    /**
     *
     */
    private void destroyIndex(IgniteEx ignite, T3<String, String, String> pair) {
        IgniteCache<?, ?> cache = ignite.getOrCreateCache(pair.get1());

        String createIdxQryStr = String.format("DROP INDEX %s", pair.get3());

        cache.query(new SqlFieldsQuery(createIdxQryStr)).getAll();
    }

    /**
     *
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    protected @interface TestIndex {
        /**
         * Index in {@link CacheBlockOnCreateDestoryIndexTest#tests} list.
         */
        int value();
    }

    /**
     * Index in {@link CacheBlockOnCreateDestoryIndexTest#tests} list.
     *
     * @see TestIndex#value()
     */
    private CacheBlockOnReadAbstractTest currentTest() {
        return tests.get(currentTestAnnotation(TestIndex.class).value());
    }
}
