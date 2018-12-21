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

package org.apache.ignite.internal.processors.metastorage;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.ignite.internal.IgniteEx;
import org.apache.ignite.internal.IgniteInternalFuture;
import org.apache.ignite.internal.processors.cache.persistence.IgniteCacheDatabaseSharedManager;
import org.apache.ignite.internal.processors.cache.persistence.metastorage.MetaStorage;
import org.apache.ignite.internal.processors.metastorage.persistence.DistributedMetaStorageImpl;
import org.apache.ignite.internal.util.typedef.internal.U;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.apache.ignite.IgniteSystemProperties.IGNITE_GLOBAL_METASTORAGE_HISTORY_MAX_BYTES;

/**
 * Test for {@link DistributedMetaStorageImpl} with enabled persistence.
 */
@RunWith(JUnit4.class)
public class DistributedMetaStoragePersistentTest extends DistributedMetaStorageTest {
    /** {@inheritDoc} */
    @Override protected boolean isPersistent() {
        return true;
    }

    /** {@inheritDoc} */
    @Override protected void beforeTest() throws Exception {
        super.beforeTest();

        cleanPersistenceDir();
    }

    /** {@inheritDoc} */
    @Override protected void afterTest() throws Exception {
        super.afterTest();

        cleanPersistenceDir();
    }

    /** */
    @Test
    public void testRestart() throws Exception {
        IgniteEx ignite = startGrid(0);

        ignite.cluster().active(true);

        ignite.context().globalMetastorage().write("key", "value");

        Thread.sleep(150L); // Remove later.

        stopGrid(0);

        ignite = startGrid(0);

        ignite.cluster().active(true);

        assertEquals("value", ignite.context().globalMetastorage().read("key"));
    }

    /** */
    @Test
    public void testJoinDirtyNode() throws Exception {
        IgniteEx ignite = startGrid(0);

        startGrid(1);

        ignite.cluster().active(true);

        ignite.context().globalMetastorage().write("key1", "value1");

        Thread.sleep(150L); // Remove later.

        stopGrid(1);

        stopGrid(0);

        ignite = startGrid(0);

        ignite.cluster().active(true);

        ignite.context().globalMetastorage().write("key2", "value2");

        Thread.sleep(150L); // Remove later.

        IgniteEx newNode = startGrid(1);

        assertEquals("value1", newNode.context().globalMetastorage().read("key1"));

        assertEquals("value2", newNode.context().globalMetastorage().read("key2"));

        assertGlobalMetastoragesAreEqual(ignite, newNode);
    }

    /** */
    @Test
    public void testJoinDirtyNodeFullData() throws Exception {
        System.setProperty(IGNITE_GLOBAL_METASTORAGE_HISTORY_MAX_BYTES, "0");

        try {
            IgniteEx ignite = startGrid(0);

            startGrid(1);

            ignite.cluster().active(true);

            ignite.context().globalMetastorage().write("key1", "value1");

            Thread.sleep(150L); // Remove later.

            stopGrid(1);

            stopGrid(0);

            ignite = startGrid(0);

            ignite.cluster().active(true);

            ignite.context().globalMetastorage().write("key2", "value2");

            ignite.context().globalMetastorage().write("key3", "value3");

            Thread.sleep(150L); // Remove later.

            IgniteEx newNode = startGrid(1);

            assertEquals("value1", newNode.context().globalMetastorage().read("key1"));

            assertEquals("value2", newNode.context().globalMetastorage().read("key2"));

            assertEquals("value3", newNode.context().globalMetastorage().read("key3"));

            assertGlobalMetastoragesAreEqual(ignite, newNode);
        }
        finally {
            System.clearProperty(IGNITE_GLOBAL_METASTORAGE_HISTORY_MAX_BYTES);
        }
    }

    /** */
    @Test
    public void testJoinNodeWithLongerHistory() throws Exception {
        IgniteEx ignite = startGrid(0);

        startGrid(1);

        ignite.cluster().active(true);

        ignite.context().globalMetastorage().write("key1", "value1");

        Thread.sleep(150L); // Remove later.

        stopGrid(1);

        ignite.context().globalMetastorage().write("key2", "value2");

        Thread.sleep(150L); // Remove later.

        stopGrid(0);

        ignite = startGrid(1);

        startGrid(0);

        awaitPartitionMapExchange();

        assertEquals("value1", ignite.context().globalMetastorage().read("key1"));

        assertEquals("value2", ignite.context().globalMetastorage().read("key2"));

        assertGlobalMetastoragesAreEqual(ignite, grid(0));
    }

    /** */
    @Test
    public void testNamesCollision() throws Exception {
        IgniteEx ignite = startGrid(0);

        ignite.cluster().active(true);

        IgniteCacheDatabaseSharedManager dbSharedMgr = ignite.context().cache().context().database();

        MetaStorage locMetastorage = dbSharedMgr.metaStorage();

        DistributedMetaStorage globalMetastorage = ignite.context().globalMetastorage();

        dbSharedMgr.checkpointReadLock();

        try {
            locMetastorage.write("key", "localValue");
        }
        finally {
            dbSharedMgr.checkpointReadUnlock();
        }

        globalMetastorage.write("key", "globalValue");

        Thread.sleep(150L); // Remove later.

        dbSharedMgr.checkpointReadLock();

        try {
            assertEquals("localValue", locMetastorage.read("key"));
        }
        finally {
            dbSharedMgr.checkpointReadUnlock();
        }

        assertEquals("globalValue", globalMetastorage.read("key"));
    }

    /** */
    @Test
    public void testUnstableTopology() throws Exception {
        int cnt = 8;

        startGridsMultiThreaded(cnt);

        grid(0).cluster().active(true);

        AtomicInteger gridIdxCntr = new AtomicInteger(0);

        AtomicBoolean stop = new AtomicBoolean();

        IgniteInternalFuture<?> fut = multithreadedAsync(() -> {
            int gridIdx = gridIdxCntr.incrementAndGet();

            try {
                while (!stop.get()) {
                    stopGrid(gridIdx, true);

                    Thread.sleep(10L);

                    startGrid(gridIdx);

                    Thread.sleep(10L);
                }
            }
            catch (Exception e) {
                log.error(e.getMessage(), e);
            }
        }, cnt - 1);

        long start = System.currentTimeMillis();

        long duration = 5_000L;

        try {
            int i = 0;
            while (System.currentTimeMillis() < start + duration)
                grid(0).context().globalMetastorage().write(
                    "key" + ++i, Integer.toString(ThreadLocalRandom.current().nextInt(1000))
                );
        }
        finally {
            stop.set(true);

            fut.get();
        }

        awaitPartitionMapExchange();

        Thread.sleep(3_000L); // Remove later.

        for (int i = 0; i < cnt; i++) {
            DistributedMetaStorage globalMetastorage = grid(i).context().globalMetastorage();

            assertNull(U.field(globalMetastorage, "startupExtras"));
        }

        for (int i = 1; i < cnt; i++)
            assertGlobalMetastoragesAreEqual(grid(0), grid(i));
    }

    /** */
    @Test
    public void testWrongStartOrder1() throws Exception {
        System.setProperty(IGNITE_GLOBAL_METASTORAGE_HISTORY_MAX_BYTES, "0");

        try {
            int cnt = 4;

            startGridsMultiThreaded(cnt);

            grid(0).cluster().active(true);

            metastorage(2).write("key1", "value1");

            stopGrid(2);

            metastorage(1).write("key2", "value2");

            stopGrid(1);

            metastorage(0).write("key3", "value3");

            stopGrid(0);

            metastorage(3).write("key4", "value4");

            stopGrid(3);


            for (int i = 0; i < cnt; i++)
                startGrid(i);

            awaitPartitionMapExchange();

            for (int i = 1; i < cnt; i++)
                assertGlobalMetastoragesAreEqual(grid(0), grid(i));
        }
        finally {
            System.clearProperty(IGNITE_GLOBAL_METASTORAGE_HISTORY_MAX_BYTES);
        }
    }

    /** */
    @Test
    public void testWrongStartOrder2() throws Exception {
        System.setProperty(IGNITE_GLOBAL_METASTORAGE_HISTORY_MAX_BYTES, "0");

        try {
            int cnt = 6;

            startGridsMultiThreaded(cnt);

            grid(0).cluster().active(true);

            metastorage(4).write("key1", "value1");

            stopGrid(4);

            metastorage(3).write("key2", "value2");

            stopGrid(3);

            metastorage(0).write("key3", "value3");

            stopGrid(0);

            stopGrid(2);

            metastorage(1).write("key4", "value4");

            stopGrid(1);

            metastorage(5).write("key5", "value5");

            stopGrid(5);


            startGrid(1);

            startGrid(0);

            stopGrid(1);

            for (int i = 1; i < cnt; i++)
                startGrid(i);

            awaitPartitionMapExchange();

            for (int i = 1; i < cnt; i++)
                assertGlobalMetastoragesAreEqual(grid(0), grid(i));
        }
        finally {
            System.clearProperty(IGNITE_GLOBAL_METASTORAGE_HISTORY_MAX_BYTES);
        }
    }

    /** */
    @Test
    public void testWrongStartOrder3() throws Exception {
        System.setProperty(IGNITE_GLOBAL_METASTORAGE_HISTORY_MAX_BYTES, "0");

        try {
            int cnt = 5;

            startGridsMultiThreaded(cnt);

            grid(0).cluster().active(true);

            metastorage(3).write("key1", "value1");

            stopGrid(3);

            stopGrid(0);

            metastorage(2).write("key2", "value2");

            stopGrid(2);

            metastorage(1).write("key3", "value3");

            stopGrid(1);

            metastorage(4).write("key4", "value4");

            stopGrid(4);


            startGrid(1);

            startGrid(0);

            stopGrid(1);

            for (int i = 1; i < cnt; i++)
                startGrid(i);

            awaitPartitionMapExchange();

            for (int i = 1; i < cnt; i++)
                assertGlobalMetastoragesAreEqual(grid(0), grid(i));
        }
        finally {
            System.clearProperty(IGNITE_GLOBAL_METASTORAGE_HISTORY_MAX_BYTES);
        }
    }

    /** */
    @Test
    public void testWrongStartOrder4() throws Exception {
        System.setProperty(IGNITE_GLOBAL_METASTORAGE_HISTORY_MAX_BYTES, "0");

        try {
            int cnt = 6;

            startGridsMultiThreaded(cnt);

            grid(0).cluster().active(true);

            metastorage(4).write("key1", "value1");

            stopGrid(4);

            stopGrid(0);

            metastorage(3).write("key2", "value2");

            stopGrid(3);

            metastorage(2).write("key3", "value3");

            stopGrid(2);

            metastorage(1).write("key4", "value4");

            stopGrid(1);

            metastorage(5).write("key5", "value5");

            stopGrid(5);


            startGrid(2);

            startGrid(0);

            stopGrid(2);

            for (int i = 1; i < cnt; i++)
                startGrid(i);

            awaitPartitionMapExchange();

            for (int i = 1; i < cnt; i++)
                assertGlobalMetastoragesAreEqual(grid(0), grid(i));
        }
        finally {
            System.clearProperty(IGNITE_GLOBAL_METASTORAGE_HISTORY_MAX_BYTES);
        }
    }
}
