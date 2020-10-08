/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.internal.processors.query.calcite.rel.logical;

import java.util.ArrayList;
import java.util.List;
import static org.apache.ignite.internal.processors.query.calcite.util.RexUtils.buildIndexCondition;
import static org.apache.ignite.internal.processors.query.calcite.util.RexUtils.buildIndexConditions;
import com.google.common.collect.ImmutableList;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelOptTable;
import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.rel.RelCollation;
import org.apache.calcite.rel.RelCollationTraitDef;
import org.apache.calcite.rel.RelInput;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.util.ImmutableBitSet;
import org.apache.ignite.internal.processors.query.calcite.rel.IgniteIndexScan;
import org.apache.ignite.internal.processors.query.calcite.rel.ProjectableFilterableTableScan;
import org.apache.ignite.internal.processors.query.calcite.trait.TraitUtils;
import org.jetbrains.annotations.Nullable;

/** */
public class IgniteLogicalIndexScan extends ProjectableFilterableTableScan {
    /** */
    private String idxName;

    /** */
    private List<RexNode> lowerIdxCond;

    /** */
    private List<RexNode> upperIdxCond;

    /** */
    private double idxSelectivity;

    /** Creates a IgniteLogicalIndexScan. */
    public static IgniteLogicalIndexScan create(
        RelOptCluster cluster,
        RelTraitSet traits,
        RelOptTable tbl,
        String idxName,
        @Nullable List<RexNode> proj,
        @Nullable RexNode cond,
        @Nullable ImmutableBitSet requiredColunms
    ) {
        IgniteLogicalIndexScan logicalIdxScan = new IgniteLogicalIndexScan(cluster, traits, tbl, idxName, proj, cond, requiredColunms);

        RelDataType rowType = logicalIdxScan.getRowType();

        RelCollation coll = TraitUtils.collation(traits);
        RelCollation collation = coll == null ? RelCollationTraitDef.INSTANCE.getDefault() : coll;

        List<RexNode> lowerIdxCond = new ArrayList<>(tbl.getRowType().getFieldCount());
        List<RexNode> upperIdxCond = new ArrayList<>(tbl.getRowType().getFieldCount());

        double idxSelectivity = buildIndexConditions(cond, collation, cluster, lowerIdxCond, upperIdxCond);

        lowerIdxCond = buildIndexCondition(lowerIdxCond, rowType, cluster);
        upperIdxCond = buildIndexCondition(upperIdxCond, rowType, cluster);

        logicalIdxScan.lowerIndexCondition(lowerIdxCond);
        logicalIdxScan.upperIndexCondition(upperIdxCond);
        logicalIdxScan.indexSelectivity(idxSelectivity);

        return logicalIdxScan;
    }

    /**
     * Constructor used for deserialization.
     *
     * @param input Serialized representation.
     */
    private IgniteLogicalIndexScan(RelInput input) {
        //super(changeTraits(input, IgniteConvention.INSTANCE));
        super(input);
    }

    /**
     * Creates a TableScan.
     * @param cluster Cluster that this relational expression belongs to
     * @param traits Traits of this relational expression
     * @param tbl Table definition.
     * @param idxName Index name.
     */
    private IgniteLogicalIndexScan(
        RelOptCluster cluster,
        RelTraitSet traits,
        RelOptTable tbl,
        String idxName) {
        this(cluster, traits, tbl, idxName, null, null, null);
    }

    /**
     * Creates a TableScan.
     * @param cluster Cluster that this relational expression belongs to
     * @param traits Traits of this relational expression
     * @param tbl Table definition.
     * @param idxName Index name.
     * @param proj Projects.
     * @param cond Filters.
     * @param requiredColunms Participating colunms.
     */
    private IgniteLogicalIndexScan(
        RelOptCluster cluster,
        RelTraitSet traits,
        RelOptTable tbl,
        String idxName,
        @Nullable List<RexNode> proj,
        @Nullable RexNode cond,
        @Nullable ImmutableBitSet requiredColunms
    ) {
        super(cluster, traits, ImmutableList.of(), tbl, proj, cond, requiredColunms);

        this.idxName = idxName;
    }

    /** */
    public String indexName() {
        return idxName;
    }

    /**
     * @param lowerIdxCond New lower index condition.
     */
    public void lowerIndexCondition(List<RexNode> lowerIdxCond) {
        this.lowerIdxCond = lowerIdxCond;
    }

    /**
     * @param upperIdxCond New upper index condition.
     */
    public void upperIndexCondition(List<RexNode> upperIdxCond) {
        this.upperIdxCond = upperIdxCond;
    }

    /**
     * @return Index selectivity.
     */
    public double indexSelectivity() {
        return idxSelectivity;
    }

    /**
     * @param idxSelectivity New index selectivity.
     */
    public void indexSelectivity(double idxSelectivity) {
        this.idxSelectivity = idxSelectivity;
    }

    /**
     * @return Lower index condition.
     */
    public List<RexNode> lowerIndexCondition() {
        return lowerIdxCond;
    }

    /**
     * @return Upper index condition.
     */
    public List<RexNode> upperIndexCondition() {
        return upperIdxCond;
    }
}
