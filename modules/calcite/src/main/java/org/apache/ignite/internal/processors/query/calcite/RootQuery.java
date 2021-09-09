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

package org.apache.ignite.internal.processors.query.calcite;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.apache.calcite.plan.Context;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.tools.Frameworks;
import org.apache.calcite.util.CancelFlag;
import org.apache.ignite.IgniteException;
import org.apache.ignite.IgniteLogger;
import org.apache.ignite.cache.query.QueryCancelledException;
import org.apache.ignite.internal.cluster.ClusterTopologyCheckedException;
import org.apache.ignite.internal.processors.cache.query.IgniteQueryErrorCode;
import org.apache.ignite.internal.processors.query.GridQueryCancel;
import org.apache.ignite.internal.processors.query.IgniteSQLException;
import org.apache.ignite.internal.processors.query.QueryContext;
import org.apache.ignite.internal.processors.query.calcite.exec.ExecutionContext;
import org.apache.ignite.internal.processors.query.calcite.exec.ExecutionServiceImpl;
import org.apache.ignite.internal.processors.query.calcite.exec.rel.Node;
import org.apache.ignite.internal.processors.query.calcite.exec.rel.RootNode;
import org.apache.ignite.internal.processors.query.calcite.prepare.BaseQueryContext;
import org.apache.ignite.internal.processors.query.calcite.prepare.Fragment;
import org.apache.ignite.internal.processors.query.calcite.prepare.MultiStepPlan;
import org.apache.ignite.internal.processors.query.calcite.prepare.PlanningContext;
import org.apache.ignite.internal.processors.query.calcite.util.Commons;
import org.apache.ignite.internal.util.typedef.F;

import static org.apache.ignite.internal.processors.query.calcite.CalciteQueryProcessor.FRAMEWORK_CONFIG;

/** */
public class RootQuery<Row> extends Query<Row> {
    /** SQL query. */
    private final String sql;

    /** Schema. */
    private final SchemaPlus schema;

    /** Parameters. */
    private final Object[] params;

    /** Logger. */
    private final IgniteLogger log;

    /** remote nodes */
    private final Set<UUID> remotes;

    /** node to fragment */
    private final Set<RemoteFragmentKey> waiting;

    /** */
    private volatile RootNode<Row> root;

    /** */
    private volatile QueryState state;

    /** */
    private volatile PlanningContext pctx;

    /** */
    private final BaseQueryContext ctx;

    /** */
    public RootQuery(
        String sql,
        SchemaPlus schema,
        Object[] params,
        QueryContext qryCtx,
        Consumer<Query> unregister,
        IgniteLogger log
    ) {
        super(UUID.randomUUID(), qryCtx != null? qryCtx.unwrap(GridQueryCancel.class) : null, unregister);

        this.sql = sql;
        this.schema = schema;
        this.params = params;
        this.log = log;

        remotes = new HashSet<>();
        waiting = new HashSet<>();

        Context parent = Commons.convert(qryCtx);

        ctx = BaseQueryContext.builder()
            .parentContext(parent)
            .frameworkConfig(
                Frameworks.newConfigBuilder(FRAMEWORK_CONFIG)
                    .defaultSchema(schema)
                    .build()
            )
            .logger(log)
            .build();
    }

    /** */
    public BaseQueryContext context() {
        return ctx;
    }

    /** */
    public String sql() {
        return sql;
    }

    /** */
    public Object[] parameters() {
        return params;
    }

    /** */
    public void run(ExecutionContext<Row> ctx, MultiStepPlan plan, Node<Row> root) {
        RootNode<Row> rootNode = new RootNode<>(ctx, plan.fieldsMetadata().rowType(), this::tryClose);
        rootNode.register(root);

        addFragment(new RunningFragment<>(F.first(plan.fragments()).root(), rootNode, ctx));

        this.root = rootNode;

        for (int i = 1; i < plan.fragments().size(); i++) {
            Fragment fragment = plan.fragments().get(i);
            List<UUID> nodes = plan.mapping(fragment).nodeIds();

            remotes.addAll(nodes);

            for (UUID node : nodes)
                waiting.add(new RemoteFragmentKey(node, fragment.fragmentId()));
        }

        state = QueryState.RUNNING;
    }

    /**
     * Can be called multiple times after receive each error
     * at {@link ExecutionServiceImpl#onResponse(ExecutionServiceImpl.RemoteFragmentKey, Throwable)}.
     */
    private void tryClose() {
        QueryState state0 = null;

        synchronized (this) {
            if (state == QueryState.CLOSED)
                return;

            if (state == QueryState.RUNNING)
                state0 = state = QueryState.CLOSING;

            // 1) close local fragment
            root.closeInternal();

            if (state == QueryState.CLOSING && waiting.isEmpty())
                state0 = state = QueryState.CLOSED;
        }

        if (state0 == QueryState.CLOSED) {
            // 2) unregister runing query
            unregister.accept(this);

            IgniteException wrpEx = null;

            // 3) close remote fragments
            for (UUID nodeId : remotes) {
//                try {
//                    exchangeService().closeOutbox(nodeId, ctx.queryId(), -1, -1);
//                }
//                catch (IgniteCheckedException e) {
//                    if (wrpEx == null)
//                        wrpEx = new IgniteException("Failed to send cancel message. [nodeId=" + nodeId + ']', e);
//                    else
//                        wrpEx.addSuppressed(e);
//                }
            }

            // 4) Cancel local fragment

            fragments.forEach(f -> f.context().execute(f.context()::cancel, f.root()::onError));

//            root.context().execute(ctx::cancel, root::onError);

            if (wrpEx != null)
                throw wrpEx;
        }
    }

    /** */
    public PlanningContext planningContext() {
        if (pctx == null) {
            pctx = PlanningContext.builder()
                .parentContext(ctx)
                .query(sql)
                .parameters(params)
                .build();

            try {
                cancel.add(() -> pctx.unwrap(CancelFlag.class).requestCancel());
            }
            catch (QueryCancelledException e) {
                throw new IgniteSQLException(e.getMessage(), IgniteQueryErrorCode.QUERY_CANCELED, e);
            }
        }

        return pctx;
    }

    /** */
    public Iterator<Row> iterator() {
        return root;
    }

    /** */
    public void onNodeLeft(UUID nodeId) {
        List<RemoteFragmentKey> fragments = null;

        synchronized (this) {
            for (RemoteFragmentKey fragment : waiting) {
                if (!fragment.nodeId().equals(nodeId))
                    continue;

                if (fragments == null)
                    fragments = new ArrayList<>();

                fragments.add(fragment);
            }
        }

        if (!F.isEmpty(fragments)) {
            ClusterTopologyCheckedException ex = new ClusterTopologyCheckedException(
                "Failed to start query, node left. nodeId=" + nodeId);

            for (RemoteFragmentKey fragment : fragments)
                onResponse(fragment, ex);
        }
    }

    /** */
    public void onResponse(UUID nodeId, long fragmentId, Throwable error) {
        onResponse(new RemoteFragmentKey(nodeId, fragmentId), error);
    }

    /** */
    private void onResponse(RemoteFragmentKey fragment, Throwable error) {
        QueryState state;
        synchronized (this) {
            waiting.remove(fragment);
            state = this.state;
        }

        if (error != null)
            onError(error);
        else if (state == QueryState.CLOSING)
            tryClose();
    }

    /** */
    public void onError(Throwable error) {
        root.onError(error);

        tryClose();
    }

    /** */
    public static PlanningContext createPlanningContext(BaseQueryContext ctx, String qry, Object[] params) {
        return PlanningContext.builder()
            .parentContext(ctx)
            .query(qry)
            .parameters(params)
            .build();
    }
}
