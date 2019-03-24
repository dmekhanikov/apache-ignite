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

package org.apache.ignite.internal.processors.cache.transactions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;
import org.apache.ignite.internal.IgniteInternalFuture;
import org.apache.ignite.internal.processors.cache.PartitionUpdateCounter;
import org.apache.ignite.internal.processors.cache.PartitionUpdateCounterImpl;
import org.apache.ignite.testframework.junits.common.GridCommonAbstractTest;
import org.junit.Test;

/**
 * Simple partition counter tests.
 */
public class PartitionUpdateCounterTest extends GridCommonAbstractTest {
    /**
     * Create new counter.
     * @return Counter.
     */
    protected PartitionUpdateCounter newCounter() {
        return new PartitionUpdateCounterImpl(log);
    }

    /**
     * Test applying update multiple times in random order.
     */
    @Test
    public void testRandomUpdates() {
        List<int[]> tmp = generateUpdates(1000, 5);

        long expTotal = tmp.stream().mapToInt(pair -> pair[1]).sum();

        PartitionUpdateCounter pc = null;

        for (int i = 0; i < 100; i++) {
            Collections.shuffle(tmp);

            PartitionUpdateCounter pc0 = newCounter();

            for (int[] pair : tmp)
                pc0.update(pair[0], pair[1]);

            if (pc == null)
                pc = pc0;
            else {
                assertEquals(pc, pc0);
                assertEquals(expTotal, pc0.get());
                assertTrue(pc0.sequential());

                pc = pc0;
            }
        }
    }

    /**
     * Test if pc correctly reports stale (before current counter) updates.
     * This information is used for logging rollback records only once.
     */
    @Test
    public void testStaleUpdate() {
        PartitionUpdateCounter pc = newCounter();

        assertTrue(pc.update(0, 1));
        assertFalse(pc.update(0, 1));

        assertTrue(pc.update(2, 1));
        assertFalse(pc.update(2, 1));

        assertTrue(pc.update(1, 1));
        assertFalse(pc.update(1, 1));
    }

    /**
     * Test multithreaded updates of pc in various modes.
     *
     * @throws Exception If failed.
     */
    @Test
    public void testMixedModeMultithreaded() throws Exception {
        PartitionUpdateCounter pc = newCounter();

        AtomicBoolean stop = new AtomicBoolean();

        Queue<long[]> reservations = new ConcurrentLinkedQueue<>();

        LongAdder reserveCntr = new LongAdder();

        IgniteInternalFuture<?> fut = multithreadedAsync(() -> {
            while(!stop.get() || !reservations.isEmpty()) {
                if (!stop.get() && ThreadLocalRandom.current().nextBoolean()) {
                    int size = ThreadLocalRandom.current().nextInt(9) + 1;

                    reservations.add(new long[] {pc.reserve(size), size}); // Only update if stop flag is set.

                    reserveCntr.add(size);
                }
                else {
                    long[] reserved = reservations.poll();

                    if (reserved == null)
                        continue;

                    pc.update(reserved[0], reserved[1]);
                }
            }
        }, Runtime.getRuntime().availableProcessors() * 2, "updater-thread");

        doSleep(10_000);

        stop.set(true);

        fut.get();

        assertTrue(reservations.isEmpty());

        log.info("counter=" + pc.toString() + ", reserveCntrLocal=" + reserveCntr.sum());

        assertTrue(pc.sequential());

        assertTrue(pc.get() == pc.reserved());

        assertEquals(reserveCntr.sum(), pc.get());
    }

    @Test
    public void testMaxGaps() {
        PartitionUpdateCounter pc = newCounter();

        int i;
        for (i = 1; i <= PartitionUpdateCounterImpl.MAX_GAPS; i++)
            pc.update(i*3, i*3 + 1);

        i++;
        try {
            pc.update(i * 3, i * 3 + 1);

            fail();
        }
        catch (Exception e) {
            // Expected.
        }
    }

    /**
     *
     */
    @Test
    public void testFoldIntermediateUpdates() {
        PartitionUpdateCounter pc = newCounter();

        pc.update(0, 59);

        pc.update(60, 5);

        pc.update(67, 3);

        pc.update(65, 2);

        Iterator<long[]> it = pc.iterator();
        it.next();

        assertFalse(it.hasNext());

        pc.update(59, 1);

        assertTrue(pc.sequential());
    }

    /**
     *
     */
    @Test
    public void testGapsIterator() {
        PartitionUpdateCounter pc = newCounter();

        pc.update(67, 3);

        pc.update(1, 58);

        pc.update(60, 5);

        Iterator<long[]> iter = pc.iterator();

        long[] gap = iter.next();

        assertEquals(1, gap[0]);
        assertEquals(58, gap[1]);

        gap = iter.next();

        assertEquals(60, gap[0]);
        assertEquals(5, gap[1]);

        gap = iter.next();

        assertEquals(67, gap[0]);
        assertEquals(3, gap[1]);

        assertFalse(iter.hasNext());
    }

    /**
     *
     */
    @Test
    public void testOverlap() {
        PartitionUpdateCounter pc = newCounter();

        assertTrue(pc.update(13, 3));

        assertTrue(pc.update(6, 7));

        assertFalse(pc.update(13, 3));

        assertFalse(pc.update(6, 7));

        Iterator<long[]> iter = pc.iterator();
        assertTrue(iter.hasNext());

        long[] gap = iter.next();

        assertEquals(6, gap[0]);
        assertEquals(10, gap[1]);

        assertFalse(iter.hasNext());
    }

    /**
     * @param cnt Count.
     * @param maxTxSize Max tx size.
     */
    private List<int[]> generateUpdates(int cnt, int maxTxSize) {
        int[] ints = new Random().ints(cnt, 1, maxTxSize + 1).toArray();

        int off = 0;

        List<int[]> res = new ArrayList<>(cnt);

        for (int i = 0; i < ints.length; i++) {
            int val = ints[i];

            res.add(new int[] {off, val});

            off += val;
        }

        return res;
    }
}
