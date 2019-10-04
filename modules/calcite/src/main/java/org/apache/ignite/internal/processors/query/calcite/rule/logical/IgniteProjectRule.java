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

package org.apache.ignite.internal.processors.query.calcite.rule.logical;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.calcite.plan.Convention;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelOptRule;
import org.apache.calcite.plan.RelOptRuleCall;
import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.plan.volcano.RelSubset;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.RelFactories;
import org.apache.calcite.rel.logical.LogicalProject;
import org.apache.calcite.rel.metadata.RelMetadataQuery;
import org.apache.calcite.rex.RexNode;
import org.apache.ignite.internal.processors.query.calcite.metadata.IgniteMdDistribution;
import org.apache.ignite.internal.processors.query.calcite.rel.IgniteRel;
import org.apache.ignite.internal.processors.query.calcite.rel.logical.IgniteLogicalProject;
import org.apache.ignite.internal.processors.query.calcite.trait.IgniteDistributionTraitDef;

/**
 *
 */
public class IgniteProjectRule extends RelOptRule {
    public static final RelOptRule INSTANCE = new IgniteProjectRule();

    private  <R extends RelNode> IgniteProjectRule() {
        super(operand(LogicalProject.class, Convention.NONE, any()), RelFactories.LOGICAL_BUILDER, "IgniteProjectRule");
    }

    @Override public void onMatch(RelOptRuleCall call) {
        LogicalProject project = call.rel(0);
        final RelNode input = project.getInput();
        final RelOptCluster cluster = input.getCluster();
        final List<RexNode> projects = project.getProjects();
        final RelMetadataQuery mq = cluster.getMetadataQuery();

        boolean done = false;

        Set<RelNode> transformed = new HashSet<>();

        for (RelNode relNode : ((RelSubset) input).getRelList()) {
            if (!(relNode instanceof IgniteRel))
                continue;

            final RelTraitSet traitSet =
                cluster.traitSet()
                    .replace(IgniteRel.LOGICAL_CONVENTION)
                    .replaceIf(IgniteDistributionTraitDef.INSTANCE,
                        () -> IgniteMdDistribution.project(mq, relNode, projects));

            RelNode converted = convert(relNode, traitSet);

            if (transformed.add(converted)) {
                call.transformTo(new IgniteLogicalProject(cluster, traitSet, converted, projects, project.getRowType()));

                done = true;
            }
        }

        if (!done) {
            final RelTraitSet traitSet =
                cluster.traitSet()
                    .replace(IgniteRel.LOGICAL_CONVENTION)
                    .replaceIf(IgniteDistributionTraitDef.INSTANCE,
                        () -> IgniteMdDistribution.project(mq, input, projects));

            RelNode converted = convert(input, traitSet);

            call.transformTo(new IgniteLogicalProject(cluster, converted.getTraitSet(), converted, projects, project.getRowType()));
        }
    }
}
