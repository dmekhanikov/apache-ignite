/*
 * Copyright 2019 GridGain Systems, Inc. and Contributors.
 *
 * Licensed under the GridGain Community Edition License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.gridgain.com/products/software/community-edition/gridgain-community-edition-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.internal.processors.query.calcite.serialize;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.CorrelationId;
import org.apache.ignite.internal.processors.query.calcite.rel.IgniteFilter;
import org.apache.ignite.internal.util.typedef.F;

/**
 *
 */
public class FilterNode extends RelGraphNode {
    private final int[] variables;
    private final LogicalExpression condition;

    private FilterNode(LogicalExpression condition, int[] variables) {
        this.variables = variables;
        this.condition = condition;
    }

    public static FilterNode create(IgniteFilter rel, RexToExpTranslator expTranslator) {
        return new FilterNode(expTranslator.translate(rel.getCondition()),
            rel.getVariablesSet().stream().mapToInt(CorrelationId::getId).toArray());
    }

    @Override public RelNode toRel(ConversionContext ctx, List<RelNode> children) {
        return IgniteFilter.create(
            F.first(children),
            condition.implement(ctx.expressionTranslator()),
            Arrays.stream(variables).mapToObj(CorrelationId::new).collect(Collectors.toSet()));
    }
}
