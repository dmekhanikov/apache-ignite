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

package org.apache.ignite.internal.processors.query.calcite.exec.rel;

import java.util.Arrays;
import java.util.Iterator;
import java.util.UUID;

import org.apache.calcite.rel.RelCollations;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.util.ImmutableIntList;
import org.apache.ignite.internal.processors.query.calcite.exec.ExecutionContext;
import org.apache.ignite.internal.processors.query.calcite.type.IgniteTypeFactory;
import org.apache.ignite.internal.processors.query.calcite.util.TypeUtils;
import org.apache.ignite.internal.util.lang.GridTuple3;
import org.apache.ignite.internal.util.typedef.F;
import org.apache.ignite.internal.util.typedef.internal.U;
import org.apache.ignite.testframework.junits.WithSystemProperty;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;

/**
 *
 */
@SuppressWarnings("TypeMayBeWeakened")
@WithSystemProperty(key = "calcite.debug", value = "true")
public class IndexSpoolTest extends AbstractExecutionTest {
    /**
     * @throws Exception If failed.
     */
    @Before
    @Override public void setup() throws Exception {
        nodesCnt = 1;
        super.setup();
    }

    /**
     *
     */
    @Test
    public void testIndexSpool() throws Exception {
        ExecutionContext<Object[]> ctx = executionContext(F.first(nodes()), UUID.randomUUID(), 0);
        IgniteTypeFactory tf = ctx.getTypeFactory();
        RelDataType rowType = TypeUtils.createRowType(tf, int.class, String.class, int.class);

        int inBufSize = U.field(AbstractNode.class, "IN_BUFFER_SIZE");

        int[] sizes = {1, inBufSize / 2 - 1, inBufSize / 2, inBufSize / 2 + 1, inBufSize, inBufSize + 1, inBufSize * 4};

        for (int size : sizes) {
            // (lower, upper, expected result size)
            GridTuple3<Object[], Object[], Integer>[] testBounds;

            if (size == 1) {
                testBounds = new GridTuple3[] {
                    new GridTuple3(new Object[] {null, null, null}, new Object[] {null, null, null}, 1),
                    new GridTuple3(new Object[] {0, null, null}, new Object[] {0, null, null}, 1)
                };
            }
            else {
                testBounds = new GridTuple3[] {
                    new GridTuple3(
                        new Object[] {null, null, null},
                        new Object[] {null, null, null},
                        size
                    ),
                    new GridTuple3(
                        new Object[] {size / 2, null, null},
                        new Object[] {size / 2, null, null},
                        1
                    ),
                    new GridTuple3(
                        new Object[] {size / 2 + 1, null, null},
                        new Object[] {size / 2 + 1, null, null},
                        1
                    ),
                    new GridTuple3(
                        new Object[] {size / 2, null, null},
                        new Object[] {null, null, null},
                        size - size / 2
                    ),
                    new GridTuple3(
                        new Object[] {null, null, null},
                        new Object[] {size / 2, null, null},
                        size / 2 + 1
                    ),
                };
            }

            log.info("Check: size=" + size);

            ScanNode<Object[]> scan = new ScanNode<>(ctx, rowType, new TestTable(size, rowType) {
                boolean first = true;

                @Override public @NotNull Iterator<Object[]> iterator() {
                    assertTrue("Rewind right", first);

                    first = false;
                    return super.iterator();
                }
            });

            Object[] lower = new Object[3];
            Object[] upper = new Object[3];

            IndexSpoolNode<Object[]> spool = new IndexSpoolNode<>(
                ctx,
                rowType,
                RelCollations.of(ImmutableIntList.of(0)),
                (o1, o2) -> o1[0] != null ? ((Comparable)o1[0]).compareTo(o2[0]) : 0,
                () -> lower,
                () -> upper
            );

            spool.register(Arrays.asList(scan));

            RootRewindable<Object[]> root = new RootRewindable<>(ctx, rowType);
            root.register(spool);

            for (GridTuple3<Object[], Object[], Integer> bound : testBounds) {
                // Set up bounds
                System.arraycopy(bound.get1(), 0, lower, 0, lower.length);
                System.arraycopy(bound.get2(), 0, upper, 0, lower.length);

                int cnt = 0;

                while (root.hasNext()) {
                    root.next();

                    cnt++;
                }

                assertEquals(
                    "Invalid result size",
                    (int)bound.get3(),
                    cnt);

                root.rewind();
            }
        }
    }
}
