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

package org.apache.ignite.internal.processors.query.calcite.planner;

import java.util.List;

import org.apache.calcite.plan.RelOptUtil;
import org.apache.calcite.rel.RelCollations;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.rex.RexFieldAccess;
import org.apache.calcite.rex.RexLiteral;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.util.ImmutableIntList;
import org.apache.ignite.internal.processors.query.calcite.rel.IgniteIndexSpool;
import org.apache.ignite.internal.processors.query.calcite.rel.IgniteRel;
import org.apache.ignite.internal.processors.query.calcite.rel.IgniteSort;
import org.apache.ignite.internal.processors.query.calcite.schema.IgniteSchema;
import org.apache.ignite.internal.processors.query.calcite.trait.IgniteDistribution;
import org.apache.ignite.internal.processors.query.calcite.trait.IgniteDistributions;
import org.apache.ignite.internal.processors.query.calcite.type.IgniteTypeFactory;
import org.apache.ignite.internal.processors.query.calcite.type.IgniteTypeSystem;
import org.junit.Test;

/**
 *
 */
@SuppressWarnings({"TooBroadScope", "FieldCanBeLocal", "TypeMayBeWeakened"})
public class IndexSpoolTest extends AbstractPlannerTest {
    /**
     * Check equi-join on not collocated fields.
     * CorrelatedNestedLoopJoinTest is applicable for this case only with IndexSpool.
     */
    @Test
    public void test() throws Exception {
        IgniteSchema publicSchema = new IgniteSchema("PUBLIC");
        IgniteTypeFactory f = new IgniteTypeFactory(IgniteTypeSystem.INSTANCE);

        publicSchema.addTable(
            "T0",
            new TestTable(
                new RelDataTypeFactory.Builder(f)
                    .add("ID", f.createJavaType(Integer.class))
                    .add("JID", f.createJavaType(Integer.class))
                    .add("VAL", f.createJavaType(String.class))
                    .build()) {

                @Override public IgniteDistribution distribution() {
                    return IgniteDistributions.affinity(0, "T0", "hash");
                }
            }
                .addIndex(RelCollations.of(ImmutableIntList.of(1, 0)), "t0_jid_idx")
        );

        publicSchema.addTable(
            "T1",
            new TestTable(
                new RelDataTypeFactory.Builder(f)
                    .add("ID", f.createJavaType(Integer.class))
                    .add("JID", f.createJavaType(Integer.class))
                    .add("VAL", f.createJavaType(String.class))
                    .build()) {

                @Override public IgniteDistribution distribution() {
                    return IgniteDistributions.affinity(0, "T1", "hash");
                }
            }
                .addIndex(RelCollations.of(ImmutableIntList.of(1, 0)), "t1_jid_idx")
        );

        String sql = "select * " +
            "from t0 " +
            "join t1 on t0.jid = t1.jid";

        IgniteRel phys = physicalPlan(
            sql,
            publicSchema,
            "MergeJoinConverter", "NestedLoopJoinConverter"
        );

        System.out.println("+++\n" + RelOptUtil.toString(phys));

        checkSplitAndSerialization(phys, publicSchema);

        IgniteIndexSpool idxSpool = findFirstNode(phys, byClass(IgniteIndexSpool.class));

        List<RexNode> lBound = idxSpool.indexCondition().lowerBound();

        assertNotNull(lBound);
        assertEquals(3, lBound.size());

        assertTrue(((RexLiteral)lBound.get(0)).isNull());
        assertTrue(((RexLiteral)lBound.get(2)).isNull());
        assertTrue(lBound.get(1) instanceof RexFieldAccess);

        List<RexNode> uBound = idxSpool.indexCondition().upperBound();

        assertNotNull(uBound);
        assertEquals(3, uBound.size());

        assertTrue(((RexLiteral)uBound.get(0)).isNull());
        assertTrue(((RexLiteral)uBound.get(2)).isNull());
        assertTrue(uBound.get(1) instanceof RexFieldAccess);
    }

    /**
     * Check equi-join on not collocated fields without indexes.
     */
    @Test
    public void testSourceWithoutCollation() throws Exception {
        IgniteSchema publicSchema = new IgniteSchema("PUBLIC");
        IgniteTypeFactory f = new IgniteTypeFactory(IgniteTypeSystem.INSTANCE);

        publicSchema.addTable(
            "T0",
            new TestTable(
                new RelDataTypeFactory.Builder(f)
                    .add("ID", f.createJavaType(Integer.class))
                    .add("JID", f.createJavaType(Integer.class))
                    .add("VAL", f.createJavaType(String.class))
                    .build()) {

                @Override public IgniteDistribution distribution() {
                    return IgniteDistributions.affinity(0, "T0", "hash");
                }
            }
        );

        publicSchema.addTable(
            "T1",
            new TestTable(
                new RelDataTypeFactory.Builder(f)
                    .add("ID", f.createJavaType(Integer.class))
                    .add("JID", f.createJavaType(Integer.class))
                    .add("VAL", f.createJavaType(String.class))
                    .build()) {

                @Override public IgniteDistribution distribution() {
                    return IgniteDistributions.affinity(0, "T1", "hash");
                }
            }
        );

        String sql = "select * " +
            "from t0 " +
            "join t1 on t0.jid = t1.jid";

        IgniteRel phys = physicalPlan(
            sql,
            publicSchema,
            "MergeJoinConverter", "NestedLoopJoinConverter"
        );

        checkSplitAndSerialization(phys, publicSchema);

        IgniteIndexSpool idxSpool = findFirstNode(phys, byClass(IgniteIndexSpool.class));

        assertTrue(idxSpool.getInput() instanceof IgniteSort);

        List<RexNode> lBound = idxSpool.indexCondition().lowerBound();

        assertNotNull(lBound);
        assertEquals(3, lBound.size());

        assertTrue(((RexLiteral)lBound.get(0)).isNull());
        assertTrue(((RexLiteral)lBound.get(2)).isNull());
        assertTrue(lBound.get(1) instanceof RexFieldAccess);

        List<RexNode> uBound = idxSpool.indexCondition().upperBound();

        assertNotNull(uBound);
        assertEquals(3, uBound.size());

        assertTrue(((RexLiteral)uBound.get(0)).isNull());
        assertTrue(((RexLiteral)uBound.get(2)).isNull());
        assertTrue(uBound.get(1) instanceof RexFieldAccess);
    }
}
