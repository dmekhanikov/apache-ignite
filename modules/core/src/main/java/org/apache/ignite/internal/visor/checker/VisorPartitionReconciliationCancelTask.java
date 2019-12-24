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

package org.apache.ignite.internal.visor.checker;

import org.apache.ignite.IgniteException;
import org.apache.ignite.internal.IgniteEx;
import org.apache.ignite.internal.processors.task.GridInternal;
import org.apache.ignite.internal.visor.VisorJob;
import org.apache.ignite.internal.visor.VisorOneNodeTask;
import org.jetbrains.annotations.Nullable;

import static org.apache.ignite.Ignition.localIgnite;

/**
 * Visor partition reconciliation task.
 */
@GridInternal
public class VisorPartitionReconciliationCancelTask extends VisorOneNodeTask<Void, Void> {
    /**
     *
     */
    private static final long serialVersionUID = 0L;

    /**
     *
     */
    private static final long STOP_SESSION_ID = 0;

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Override protected VisorJob<Void, Void> job(Void arg) {
        return new VisorPartitionReconciliationCancelJob(arg, debug);
    }

    /**
     * Partition reconciliation job.
     */
    public class VisorPartitionReconciliationCancelJob extends VisorJob<Void, Void> {
        /**
         *
         */
        private static final long serialVersionUID = 0L;

        /**
         * Create job with specified argument.
         *
         * @param arg Job argument.
         * @param debug Flag indicating whether debug information should be printed into node log.
         */
        protected VisorPartitionReconciliationCancelJob(@Nullable Void arg, boolean debug) {
            super(arg, debug);
        }

        /** {@inheritDoc} */
        @Override protected Void run(@Nullable Void arg) throws IgniteException {
            ignite.compute()
                .broadcastAsync(() -> ((IgniteEx)localIgnite()).context().diagnostic().setReconciliationSessionId(STOP_SESSION_ID))
                .get();

            return null;
        }
    }
}
