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

import java.util.function.Predicate;
import org.apache.ignite.internal.processors.query.calcite.exec.ExecutionContext;
import org.apache.ignite.internal.processors.query.calcite.exec.RowHandler;

/** */
public class LeftJoinNode<Row> extends AbstractJoinNode<Row> {
    /** Right row factory. */
    private final RowHandler.RowFactory<Row> rightRowFactory;

    /** Whether current left row was matched or not. */
    private boolean matched;

    /** */
    private Row left;

    /** */
    private int rightIdx;

    /**
     * @param ctx Execution context.
     * @param cond Join expression.
     */
    public LeftJoinNode(ExecutionContext<Row> ctx, Predicate<Row> cond, RowHandler.RowFactory<Row> rightRowFactory) {
        super(ctx, cond);

        this.rightRowFactory = rightRowFactory;
    }

    /** */
    @Override protected void resetInternal() {
        matched = false;
        left = null;
        rightIdx = 0;

        super.resetInternal();
    }

    /** {@inheritDoc} */
    @Override protected void doJoinInternal() {
        if (waitingRight == NOT_WAITING) {
            inLoop = true;
            try {
                while (requested > 0 && (left != null || !leftInBuf.isEmpty())) {
                    if (left == null) {
                        left = leftInBuf.remove();

                        matched = false;
                    }

                    while (requested > 0 && rightIdx < rightMaterialized.size()) {
                        Row row = handler.concat(left, rightMaterialized.get(rightIdx++));

                        if (!cond.test(row))
                            continue;

                        requested--;
                        matched = true;
                        downstream.push(row);
                    }

                    if (rightIdx == rightMaterialized.size()) {
                        boolean wasPushed = false;

                        if (!matched && requested > 0) {
                            requested--;
                            wasPushed = true;

                            downstream.push(handler.concat(left, rightRowFactory.create()));
                        }

                        if (matched || wasPushed) {
                            left = null;
                            rightIdx = 0;
                        }
                    }
                }
            }
            finally {
                inLoop = false;
            }
        }

        if (waitingRight == 0)
            sources.get(1).request(waitingRight = IN_BUFFER_SIZE);

        if (waitingLeft == 0 && leftInBuf.isEmpty())
            sources.get(0).request(waitingLeft = IN_BUFFER_SIZE);

        if (requested > 0 && waitingLeft == NOT_WAITING && waitingRight == NOT_WAITING && left == null && leftInBuf.isEmpty()) {
            requested = 0;
            downstream.end();
        }
    }
}
