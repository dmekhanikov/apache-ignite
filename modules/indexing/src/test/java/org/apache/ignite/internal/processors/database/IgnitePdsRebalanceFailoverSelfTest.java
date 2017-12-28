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

package org.apache.ignite.internal.processors.database;

import java.util.List;
import java.util.Map;
import org.apache.ignite.Ignite;
import org.apache.ignite.cache.CacheAtomicityMode;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.cache.affinity.rendezvous.RendezvousAffinityFunction;
import org.apache.ignite.cache.query.annotations.QuerySqlField;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.configuration.MemoryConfiguration;
import org.apache.ignite.configuration.MemoryPolicyConfiguration;
import org.apache.ignite.configuration.PersistentStoreConfiguration;
import org.apache.ignite.configuration.WALMode;
import org.apache.ignite.internal.IgniteEx;
import org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtLocalPartition;
import org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtPartitionTopology;
import org.apache.ignite.internal.util.typedef.internal.CU;
import org.apache.ignite.internal.util.typedef.internal.U;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.TcpDiscoveryIpFinder;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.apache.ignite.testframework.junits.common.GridCommonAbstractTest;

import static org.apache.ignite.cache.CacheAtomicityMode.TRANSACTIONAL;
import static org.apache.ignite.cache.CacheMode.PARTITIONED;
import static org.apache.ignite.cache.CacheWriteSynchronizationMode.FULL_SYNC;
import static org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtPartitionState.RENTING;
import static org.apache.ignite.internal.processors.cache.persistence.GridCacheDatabaseSharedManager.IGNITE_PDS_CHECKPOINT_TEST_SKIP_SYNC;

/**
 * Test rebalance resilience to restarts.
 */
public class IgnitePdsRebalanceFailoverSelfTest extends GridCommonAbstractTest {
    /** */
    public static final int CACHES_IN_GROUP = 1;

    /** */
    public static final int PARTS_CNT = 32;

    /** */
    public static final String TEST_GRP_NAME = "testGrp";

    /** */
    public static final int PART_ID = 15;

    /** */
    private static TcpDiscoveryIpFinder ipFinder = new TcpDiscoveryVmIpFinder(true);

    /** {@inheritDoc} */
    @Override protected IgniteConfiguration getConfiguration(String igniteInstanceName) throws Exception {
        IgniteConfiguration cfg = super.getConfiguration(igniteInstanceName);

        cfg.setMemoryConfiguration(new MemoryConfiguration().setDefaultMemoryPolicyName("d").
            setPageSize(1024).setMemoryPolicies(new MemoryPolicyConfiguration().setName("d").
            setInitialSize(50 * 1024 * 1024L).setMaxSize(50 * 1024 * 1024)));

        cfg.setPersistentStoreConfiguration(new PersistentStoreConfiguration().setWalMode(WALMode.LOG_ONLY));

        ((TcpDiscoverySpi) cfg.getDiscoverySpi()).setIpFinder(ipFinder);

        cfg.setCacheConfiguration(testCaches());

        cfg.setConsistentId(igniteInstanceName);

        return cfg;
    }

    /** */
    private CacheConfiguration[] testCaches() {
        CacheConfiguration[] cfgs = new CacheConfiguration[CACHES_IN_GROUP];

        for (int i = 0; i < cfgs.length; i++)
            cfgs[i] = cacheConfiguration("test" + i, TRANSACTIONAL, PARTITIONED, 1, TEST_GRP_NAME);

        return cfgs;
    }

    /** {@inheritDoc} */
    @Override protected void beforeTest() throws Exception {
        deleteRecursively(U.resolveWorkDirectory(U.defaultWorkDirectory(), "db", false));
    }

    /** {@inheritDoc} */
    @Override protected void afterTest() throws Exception {
        stopAllGrids();

        deleteRecursively(U.resolveWorkDirectory(U.defaultWorkDirectory(), "db", false));
    }

    /**
     * @param name          Name.
     * @param atomicityMode Atomicity mode.
     * @param cacheMode     Cache mode.
     * @param backups       Backups.
     * @param grp           Group.
     * @return Cache configuration.
     */
    private CacheConfiguration cacheConfiguration(String name,
        CacheAtomicityMode atomicityMode,
        CacheMode cacheMode,
        int backups,
        String grp) {
        CacheConfiguration ccfg = new CacheConfiguration(name);

        ccfg.setAtomicityMode(atomicityMode);
        ccfg.setWriteSynchronizationMode(FULL_SYNC);
        ccfg.setCacheMode(cacheMode);
        ccfg.setGroupName(grp);

        ccfg.setAffinity(new RendezvousAffinityFunction(false, PARTS_CNT));

        if (cacheMode == PARTITIONED)
            ccfg.setBackups(backups);

        return ccfg;
    }

    /**
     * @throws Exception If failed.
     */
    public void testRebalanceFailover() throws Exception {
        try {
            System.setProperty(IGNITE_PDS_CHECKPOINT_TEST_SKIP_SYNC, "true");

            IgniteEx crd = startGrid(0);

            crd.active(true);

            startGrid(1);

            awaitPartitionMapExchange(true, true, null);

            final int cnt = 1000;

            final CacheConfiguration[] cfgs = testCaches();

            for (int i = 0; i < cfgs.length; i++) {
                CacheConfiguration cfg = cfgs[i];

                for (int k = 0; k < cnt; k++)
                    crd.cache(cfg.getName()).put(k, k);
            }

            int addCnt = 0;

            // Put additional items to single partition to enforce renting state on restart.
            for (int i = 0; i < cfgs.length; i++) {
                CacheConfiguration cfg = cfgs[i];

                for (int k = cnt; k < cnt * 1000; k++) {
                    if (crd.affinity(cfg.getName()).partition(k) == PART_ID) {
                        crd.cache(cfg.getName()).put(k, k);

                        addCnt++;
                    }
                }
            }

            validateCaches(crd, cfgs, cnt + addCnt);

            crd = restartAllNoRebalance(crd, 2, false);

            crd = restartAllNoRebalance(crd, 2, false);

            doSleep(1_000);

            // assertEquals(RENTING, states(crd).get(PART_ID).state());

            validateCaches(crd, cfgs, cnt + addCnt);
        }
        finally {
            System.clearProperty(IGNITE_PDS_CHECKPOINT_TEST_SKIP_SYNC);
        }
    }

    private void validateCaches(Ignite crd, CacheConfiguration[] cfgs, int cnt) {
        for (int i = 0; i < cfgs.length; i++) {
            CacheConfiguration cfg = cfgs[i];

            final int size = crd.cache(cfg.getName()).size();

            assertEquals(cnt, size);
        }
    }

    public IgniteEx restartAllNoRebalance(IgniteEx crd, int nodesToStart, boolean awaitExchange) throws Exception {
        crd.active(false);
        stopAllGrids();

        IgniteEx crd2 = startGrid(0);
        startGridsMultiThreaded(1, nodesToStart);
        crd2.active(true);

        if (awaitExchange)
            awaitPartitionMapExchange();

        return crd2;
    }

    /**
     * @param node Node.
     */
    private Map<Integer, GridDhtLocalPartition> states(IgniteEx node) {
        GridDhtPartitionTopology top = node.context().cache().context().cache().
            cacheGroup(CU.cacheId(TEST_GRP_NAME)).topology();

        List<GridDhtLocalPartition> parts = top.localPartitions();

        Map<Integer, GridDhtLocalPartition> map = U.newHashMap(parts.size());

        for (GridDhtLocalPartition part : parts)
            map.put(part.id(), part);

        return map;
    }
}
