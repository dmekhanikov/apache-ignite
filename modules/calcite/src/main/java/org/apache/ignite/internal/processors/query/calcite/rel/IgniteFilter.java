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

import java.util.List;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelOptCost;
import org.apache.calcite.plan.RelOptPlanner;
import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.rel.RelInput;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.CorrelationId;
import org.apache.calcite.rel.core.Filter;
import org.apache.calcite.rel.metadata.RelMdUtil;
import org.apache.calcite.rel.metadata.RelMetadataQuery;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.util.Pair;
import org.apache.ignite.internal.processors.query.calcite.trait.CorrelationTrait;
import org.apache.ignite.internal.processors.query.calcite.trait.TraitUtils;
import org.apache.ignite.internal.processors.query.calcite.trait.TraitsAwareIgniteRel;
import org.apache.ignite.internal.processors.query.calcite.util.RexUtils;

import static org.apache.ignite.internal.processors.query.calcite.trait.TraitUtils.changeTraits;

/**
 * Relational expression that iterates over its input
 * and returns elements for which <code>condition</code> evaluates to
 * <code>true</code>.
 *
 * <p>If the condition allows nulls, then a null value is treated the same as
 * false.</p>
 */
public class IgniteFilter extends Filter implements TraitsAwareIgniteRel {
    /**
     * Creates a filter.
     *
     * @param cluster   Cluster that this relational expression belongs to
     * @param traits    the traits of this rel
     * @param input     input relational expression
     * @param condition boolean expression which determines whether a row is
     *                  allowed to pass
     */
    public IgniteFilter(RelOptCluster cluster, RelTraitSet traits, RelNode input, RexNode condition) {
        super(cluster, traits, input, condition);
    }

    /** */
    public IgniteFilter(RelInput input) {
        super(changeTraits(input, IgniteConvention.INSTANCE));
    }

    /** {@inheritDoc} */
    @Override public Filter copy(RelTraitSet traitSet, RelNode input, RexNode condition) {
        return new IgniteFilter(getCluster(), traitSet, input, condition);
    }

    /** {@inheritDoc} */
    @Override public <T> T accept(IgniteRelVisitor<T> visitor) {
        return visitor.visit(this);
    }

    /** {@inheritDoc} */
    @Override public List<Pair<RelTraitSet, List<RelTraitSet>>> passThroughRewindability(RelTraitSet nodeTraits,
        List<RelTraitSet> inTraits) {
        return ImmutableList.of(Pair.of(nodeTraits,
            ImmutableList.of(inTraits.get(0).replace(TraitUtils.rewindability(nodeTraits)))));
    }

    /** {@inheritDoc} */
    @Override public List<Pair<RelTraitSet, List<RelTraitSet>>> passThroughDistribution(RelTraitSet nodeTraits,
        List<RelTraitSet> inTraits) {
        return ImmutableList.of(Pair.of(nodeTraits,
            ImmutableList.of(inTraits.get(0).replace(TraitUtils.distribution(nodeTraits)))));
    }

    /** {@inheritDoc} */
    @Override public List<Pair<RelTraitSet, List<RelTraitSet>>> passThroughCollation(RelTraitSet nodeTraits,
        List<RelTraitSet> inTraits) {
        return ImmutableList.of(Pair.of(nodeTraits,
            ImmutableList.of(inTraits.get(0).replace(TraitUtils.collation(nodeTraits)))));
    }

    /** {@inheritDoc} */
    @Override public List<Pair<RelTraitSet, List<RelTraitSet>>> deriveRewindability(RelTraitSet nodeTraits,
        List<RelTraitSet> inTraits) {
        if (!TraitUtils.rewindability(inTraits.get(0)).rewindable() && RexUtils.hasCorrelation(getCondition()))
            return ImmutableList.of();

        return ImmutableList.of(Pair.of(nodeTraits.replace(TraitUtils.rewindability(inTraits.get(0))),
            inTraits));
    }

    /** {@inheritDoc} */
    @Override public List<Pair<RelTraitSet, List<RelTraitSet>>> deriveDistribution(RelTraitSet nodeTraits,
        List<RelTraitSet> inTraits) {
        return ImmutableList.of(Pair.of(nodeTraits.replace(TraitUtils.distribution(inTraits.get(0))),
            inTraits));
    }

    /** {@inheritDoc} */
    @Override public List<Pair<RelTraitSet, List<RelTraitSet>>> deriveCollation(RelTraitSet nodeTraits,
        List<RelTraitSet> inTraits) {
        return ImmutableList.of(Pair.of(nodeTraits.replace(TraitUtils.collation(inTraits.get(0))),
            inTraits));
    }

    /** */
    @Override public List<Pair<RelTraitSet, List<RelTraitSet>>> passThroughCorrelation(RelTraitSet nodeTraits,
        List<RelTraitSet> inTraits) {
        Set<CorrelationId> corrSet = RexUtils.extractCorrelationIds(getCondition());

        if (corrSet.isEmpty() && TraitUtils.correlation(nodeTraits).correlated())
            return ImmutableList.of(Pair.of(nodeTraits,
                ImmutableList.of(inTraits.get(0).replace(TraitUtils.correlation(nodeTraits)))));

        if (corrSet.isEmpty()
            || RexUtils.extractCorrelationIds(getCondition()).containsAll(TraitUtils.correlation(nodeTraits).correlationIds())) {
            return ImmutableList.of(Pair.of(nodeTraits,
                ImmutableList.of(inTraits.get(0).replace(CorrelationTrait.UNCORRELATED))));
        }
        else
            return ImmutableList.of();
    }

    /** */
    @Override public List<Pair<RelTraitSet, List<RelTraitSet>>> deriveCorrelation(RelTraitSet nodeTraits,
        List<RelTraitSet> inTraits) {
        Set<CorrelationId> corrIds = RexUtils.extractCorrelationIds(getCondition());

        corrIds.addAll(TraitUtils.correlation(inTraits.get(0)).correlationIds());

        return ImmutableList.of(Pair.of(nodeTraits.replace(CorrelationTrait.correlations(corrIds)),
            inTraits));
    }

    /** {@inheritDoc} */
    @Override public RelOptCost computeSelfCost(RelOptPlanner planner, RelMetadataQuery mq) {
        double rowCount = mq.getRowCount(getInput());
        rowCount = RelMdUtil.addEpsilon(rowCount); // to differ from rel nodes with integrated filter
        return planner.getCostFactory().makeCost(rowCount, 0, 0);
    }
}
