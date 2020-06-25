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
package org.apache.ignite.internal.processors.query.calcite.rel;

import com.google.common.collect.ImmutableList;
import java.util.List;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelOptCost;
import org.apache.calcite.plan.RelOptPlanner;
import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.RelWriter;
import org.apache.calcite.rel.SingleRel;
import org.apache.calcite.rel.metadata.RelMetadataQuery;
import org.apache.calcite.rex.RexLiteral;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.util.Pair;

import static org.apache.ignite.internal.processors.query.calcite.trait.TraitUtils.fixTraits;

/**
 * Relational expression that applies a limit and/or offset to its input.
 */
public class IgniteLimit extends SingleRel implements IgniteRel {
    /** Offset. */
    public final RexNode offset;

    /** Fetches rows expression (limit) */
    public final RexNode fetch;

    /**
     * Constructor.
     *
     * @param cluster Cluster.
     * @param traits Trait set.
     * @param offset Offset.
     * @param fetch Limit.
     */
    public IgniteLimit(
        RelOptCluster cluster,
        RelTraitSet traits,
        RelNode input,
        RexNode offset,
        RexNode fetch) {
        super(cluster, traits, input);

        this.offset = offset;
        this.fetch = fetch;
    }

    /** {@inheritDoc} */
    @Override public IgniteLimit copy(
        RelTraitSet traitSet,
        List<RelNode> newInputs) {
        return new IgniteLimit(
            getCluster(),
            traitSet,
            sole(newInputs),
            offset,
            fetch);
    }

    /** {@inheritDoc} */
    @Override public RelWriter explainTerms(RelWriter pw) {
        return super.explainTerms(pw)
            .itemIf("offset", offset, offset != null)
            .itemIf("fetch", fetch, fetch != null);
    }

    /** {@inheritDoc} */
    @Override public <T> T accept(IgniteRelVisitor<T> visitor) {
        return visitor.visit(this);
    }

    /** {@inheritDoc} */
    @Override public Pair<RelTraitSet, List<RelTraitSet>> passThroughTraits(RelTraitSet required) {
        // TODO: https://issues.apache.org/jira/browse/IGNITE-13179
        required = fixTraits(required);

        return Pair.of(required, ImmutableList.of(required));
    }

    /** {@inheritDoc} */
    @Override public Pair<RelTraitSet, List<RelTraitSet>> deriveTraits(RelTraitSet childTraits, int childId) {
        // TODO: https://issues.apache.org/jira/browse/IGNITE-13179
        assert childId == 0;

        childTraits = fixTraits(childTraits);

        return Pair.of(childTraits, ImmutableList.of(childTraits));
    }

    /** {@inheritDoc} */
    @Override public RelOptCost computeSelfCost(RelOptPlanner planner, RelMetadataQuery mq) {
        double rows = estimateRowCount(mq);

        return planner.getCostFactory().makeCost(rows, 0, 0);
    }

    /** {@inheritDoc} */
    @Override public double estimateRowCount(RelMetadataQuery mq) {
        Integer lim = intFromRex(fetch);
        Integer off = intFromRex(offset);

        if (off != null)
            off *= 2;

        double inRows = input.estimateRowCount(mq);

        if (lim != null) {
            lim *= 2;

            return Math.min(lim, off != null ? Math.abs(inRows - off) : inRows);
        }
        else if (off != null)
            return inRows - off;
        else
            return inRows;
    }

    /**
     * @return Integer value of the literal expression.
     */
    private Integer intFromRex(RexNode n) {
        try {
            if (n instanceof RexLiteral)
                return ((RexLiteral)n).getValueAs(Integer.class);
            else
                return null;
        }
        catch (Exception e) {
            return null;
        }
    }
}
