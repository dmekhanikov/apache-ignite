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

package org.apache.ignite.internal.processors.cache;

import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.affinity.Affinity;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.internal.IgniteInternalFuture;
import org.apache.ignite.internal.util.typedef.internal.U;
import org.apache.ignite.lang.IgniteFuture;
import org.apache.ignite.lang.IgniteFutureTimeoutException;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.TcpDiscoveryIpFinder;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.apache.ignite.testframework.GridTestUtils;
import org.apache.ignite.testframework.junits.common.GridCommonAbstractTest;

import static org.apache.ignite.IgniteSystemProperties.IGNITE_MAX_COMPLETED_TX_COUNT;
import static org.apache.ignite.cache.CacheAtomicityMode.TRANSACTIONAL;
import static org.apache.ignite.cache.CacheMode.PARTITIONED;
import static org.apache.ignite.cache.CacheWriteSynchronizationMode.FULL_SYNC;
import static org.apache.ignite.testframework.GridTestUtils.runAsync;

/**
 *
 */
public class GridCacheMissingCommitVersionSelfTest extends GridCommonAbstractTest {
    /** */
    private volatile boolean putFailed;

    /** */
    private String maxCompletedTxCnt;

    /** IP finder. */
    private static final TcpDiscoveryIpFinder IP_FINDER = new TcpDiscoveryVmIpFinder(true);

    /** {@inheritDoc} */
    @Override protected IgniteConfiguration getConfiguration(String gridName) throws Exception {
        IgniteConfiguration cfg = super.getConfiguration(gridName);

        TcpDiscoverySpi discoSpi = new TcpDiscoverySpi();

        discoSpi.setIpFinder(IP_FINDER);

        cfg.setDiscoverySpi(discoSpi);

        CacheConfiguration ccfg = new CacheConfiguration(DEFAULT_CACHE_NAME);

        ccfg.setCacheMode(PARTITIONED);
        ccfg.setAtomicityMode(TRANSACTIONAL);
        ccfg.setWriteSynchronizationMode(FULL_SYNC);
        ccfg.setBackups(1);

        cfg.setCacheConfiguration(ccfg);

        return cfg;
    }

    /** {@inheritDoc} */
    @Override public String getTestIgniteInstanceName(int idx) {
        return "NODE-" + Integer.toString(idx);
    }

    /** {@inheritDoc} */
    @Override protected void beforeTest() throws Exception {
        super.beforeTest();

        maxCompletedTxCnt = System.getProperty(IGNITE_MAX_COMPLETED_TX_COUNT);

        System.setProperty(IGNITE_MAX_COMPLETED_TX_COUNT, String.valueOf(5));
    }

    /** {@inheritDoc} */
    @Override protected void afterTest() throws Exception {
        super.afterTest();

        System.setProperty(IGNITE_MAX_COMPLETED_TX_COUNT, maxCompletedTxCnt != null ? maxCompletedTxCnt : "");

        stopAllGrids();
    }

    /**
     * @throws Exception If failed.
     */
    public void testMissingCommitVersion() throws Exception {
        startGrid(0);

        final IgniteCache<Integer, Integer> cache = jcache(0);

        final int KEYS_PER_THREAD = 10_000;

        final AtomicInteger keyStart = new AtomicInteger();

        final ConcurrentLinkedDeque<Integer> q = new ConcurrentLinkedDeque<>();

        GridTestUtils.runMultiThreaded(new Callable<Object>() {
            @Override public Object call() throws Exception {
                int start = keyStart.getAndAdd(KEYS_PER_THREAD);

                for (int i = 0; i < KEYS_PER_THREAD && !putFailed; i++) {
                    int key = start + i;

                    try {
                        cache.put(key, 1);
                    }
                    catch (Exception e) {
                        log.info("Put failed [err=" + e + ", i=" + i + ']');

                        putFailed = true;

                        q.add(key);
                    }
                }

                return null;
            }
        }, 10, "put-thread");

        assertTrue("Test failed to provoke 'missing commit version' error.", putFailed);

        for (Integer key : q) {
            log.info("Trying to update " + key);

            IgniteFuture<?> fut = cache.putAsync(key, 2);

            try {
                fut.get(5000);
            }
            catch (IgniteFutureTimeoutException ignore) {
                fail("Put failed to finish in 5s: " + key);
            }
        }
    }

    /**
     * OnePhaseCommitAckRequest may come with missing version. It could be removed by completedVersHashMap itself if
     * reaches the maximum size under heavy load. This tests verifies IGNITE-7047 fix
     * Log can be enabled by adding org.apache.ignite.internal.processors.cache.transactions category to log4j-test.xml
     * @throws Exception If failed.
     */
    public void testMissingVersionForOnePhaseCommitAckRequest() throws Exception {
        final int GRID_SIZE = 2;

        final AtomicReference<Throwable> unexpectedE = new AtomicReference<Throwable>();

        // catch possible asserts from removeTxReturn
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            public void uncaughtException(Thread t, Throwable e) {
                log().error("uncaughtException " + e.getMessage());
                unexpectedE.set(e);
            }
        });

        startGridsMultiThreaded(GRID_SIZE);

        // run NODE-0 restarts 3 times in 100 msec
        IgniteInternalFuture<Object> restartFut = runNodeRestarts();

        // run simultaneous transactions at NODE-1
        IgniteInternalFuture<Object> txFut = runTransactions(restartFut);

        restartFut.get();
        txFut.get();

        assertEquals(null, unexpectedE.get());

        checkOnePhaseCommitReturnValuesCleaned(GRID_SIZE);
    }


    /**
     * Creates and runs a thread that restarts Node-0 every 100 ms.
     * @return future.
     */
    private IgniteInternalFuture<Object> runNodeRestarts() {
        return runAsync(new Callable<Object>() {
            @Override public Object call() throws Exception {
                U.sleep(200);

                for (int i = 0; i < 3; i++) {
                    stopGrid(0);

                    U.sleep(100);

                    startGrid(0);

                    awaitPartitionMapExchange();
                }

                return null;
            }
        });
    }

    /**
     * Creates and runs a thread that executes Cache getAndPut operations (at Node-1) in infinite loop.
     * @param fut Future that is used as a signal to stop thread infinite loop.
     * @return future.
     */
    private IgniteInternalFuture<Object> runTransactions(final IgniteInternalFuture<Object> fut) {
        final int keysCnt = 10_000;

        return runAsync(new Callable<Object>() {
            @Override public Object call() throws Exception {
                int iter = 0;

                while (!fut.isDone()) {
                    try {
                        IgniteCache<Integer, Integer> cache = ignite(1).cache(DEFAULT_CACHE_NAME);

                        Integer val = ++iter;

                        final Affinity<Integer> affinity = ignite(1).affinity(cache.getName());

                        // run transactions for primary partitions at NODE-1 to have more unexpected
                        // OnePhaseCommitAckRequest at NODE-0.
                        for (int i = 0; i < keysCnt && !fut.isDone(); i++)
                            if (affinity.mapKeyToNode(i) == ignite(1).cluster().localNode())
                                cache.getAndPut(i, val);
                    }
                    catch (Exception ignored) {
                        // No-op.
                    }
                }

                return null;
            }
        });
    }
}
