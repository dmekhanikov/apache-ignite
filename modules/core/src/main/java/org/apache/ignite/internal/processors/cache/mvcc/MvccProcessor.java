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

package org.apache.ignite.internal.processors.cache.mvcc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.IgniteException;
import org.apache.ignite.IgniteLogger;
import org.apache.ignite.cluster.ClusterNode;
import org.apache.ignite.configuration.DataRegionConfiguration;
import org.apache.ignite.configuration.DataStorageConfiguration;
import org.apache.ignite.events.DiscoveryEvent;
import org.apache.ignite.events.Event;
import org.apache.ignite.internal.GridKernalContext;
import org.apache.ignite.internal.IgniteDiagnosticPrepareContext;
import org.apache.ignite.internal.IgniteInternalFuture;
import org.apache.ignite.internal.IgniteInterruptedCheckedException;
import org.apache.ignite.internal.cluster.ClusterTopologyCheckedException;
import org.apache.ignite.internal.managers.communication.GridMessageListener;
import org.apache.ignite.internal.managers.discovery.DiscoCache;
import org.apache.ignite.internal.managers.eventstorage.GridLocalEventListener;
import org.apache.ignite.internal.pagemem.PageIdUtils;
import org.apache.ignite.internal.pagemem.PageMemory;
import org.apache.ignite.internal.processors.GridProcessorAdapter;
import org.apache.ignite.internal.processors.affinity.AffinityTopologyVersion;
import org.apache.ignite.internal.processors.cache.CacheGroupContext;
import org.apache.ignite.internal.processors.cache.GridCacheContext;
import org.apache.ignite.internal.processors.cache.GridCacheEntryEx;
import org.apache.ignite.internal.processors.cache.KeyCacheObject;
import org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtLocalPartition;
import org.apache.ignite.internal.processors.cache.mvcc.msg.MvccAckRequestQuery;
import org.apache.ignite.internal.processors.cache.mvcc.msg.MvccAckRequestTx;
import org.apache.ignite.internal.processors.cache.mvcc.msg.MvccAckRequestTxAndQuery;
import org.apache.ignite.internal.processors.cache.mvcc.msg.MvccAckRequestTxAndQueryEx;
import org.apache.ignite.internal.processors.cache.mvcc.msg.MvccActiveQueriesMessage;
import org.apache.ignite.internal.processors.cache.mvcc.msg.MvccFutureResponse;
import org.apache.ignite.internal.processors.cache.mvcc.msg.MvccMessage;
import org.apache.ignite.internal.processors.cache.mvcc.msg.MvccNewQueryAckRequest;
import org.apache.ignite.internal.processors.cache.mvcc.msg.MvccQuerySnapshotRequest;
import org.apache.ignite.internal.processors.cache.mvcc.msg.MvccSnapshotResponse;
import org.apache.ignite.internal.processors.cache.mvcc.msg.MvccTxSnapshotRequest;
import org.apache.ignite.internal.processors.cache.mvcc.msg.MvccWaitTxsRequest;
import org.apache.ignite.internal.processors.cache.mvcc.txlog.TxLog;
import org.apache.ignite.internal.processors.cache.persistence.CacheDataRowAdapter;
import org.apache.ignite.internal.processors.cache.persistence.DatabaseLifecycleListener;
import org.apache.ignite.internal.processors.cache.persistence.IgniteCacheDatabaseSharedManager;
import org.apache.ignite.internal.processors.cache.persistence.tree.io.AbstractDataPageIO;
import org.apache.ignite.internal.processors.cache.persistence.tree.io.DataPageIO;
import org.apache.ignite.internal.processors.cache.persistence.tree.io.PageIO;
import org.apache.ignite.internal.processors.cache.transactions.IgniteInternalTx;
import org.apache.ignite.internal.processors.cache.tree.mvcc.data.MvccDataRow;
import org.apache.ignite.internal.processors.cache.tree.mvcc.search.MvccLinkAwareSearchRow;
import org.apache.ignite.internal.processors.cache.version.GridCacheVersion;
import org.apache.ignite.internal.util.GridAtomicLong;
import org.apache.ignite.internal.util.GridLongList;
import org.apache.ignite.internal.util.future.GridCompoundFuture;
import org.apache.ignite.internal.util.future.GridFutureAdapter;
import org.apache.ignite.internal.util.typedef.F;
import org.apache.ignite.internal.util.typedef.T2;
import org.apache.ignite.internal.util.typedef.internal.CU;
import org.apache.ignite.internal.util.typedef.internal.U;
import org.apache.ignite.internal.util.worker.GridWorker;
import org.apache.ignite.lang.IgniteClosure;
import org.apache.ignite.lang.IgniteInClosure;
import org.apache.ignite.plugin.extensions.communication.Message;
import org.apache.ignite.spi.discovery.DiscoveryDataBag;
import org.apache.ignite.thread.IgniteThread;
import org.jetbrains.annotations.Nullable;

import static org.apache.ignite.events.EventType.EVT_CLIENT_NODE_DISCONNECTED;
import static org.apache.ignite.events.EventType.EVT_NODE_FAILED;
import static org.apache.ignite.events.EventType.EVT_NODE_LEFT;
import static org.apache.ignite.events.EventType.EVT_NODE_METRICS_UPDATED;
import static org.apache.ignite.events.EventType.EVT_NODE_SEGMENTED;
import static org.apache.ignite.internal.GridTopic.TOPIC_CACHE_COORDINATOR;
import static org.apache.ignite.internal.events.DiscoveryCustomEvent.EVT_DISCOVERY_CUSTOM_EVT;
import static org.apache.ignite.internal.managers.communication.GridIoPolicy.SYSTEM_POOL;
import static org.apache.ignite.internal.processors.cache.mvcc.txlog.TxLog.TX_LOG_CACHE_ID;
import static org.apache.ignite.internal.processors.cache.mvcc.txlog.TxLog.TX_LOG_CACHE_NAME;
import static org.apache.ignite.internal.processors.cache.persistence.tree.io.PageIO.T_DATA;

/**
 * MVCC processor.
 */
public class MvccProcessor extends GridProcessorAdapter implements DatabaseLifecycleListener {
    /** */
    public static final long MVCC_COUNTER_NA = 0L;

    /** */
    public static final long MVCC_START_CNTR = 1L;

    /** */
    private volatile MvccCoordinator curCrd;

    /** */
    private final AtomicLong mvccCntr = new AtomicLong(MVCC_START_CNTR);

    /** */
    private final GridAtomicLong committedCntr = new GridAtomicLong(1L);

    /** */
    // TODO: Why do we need GridCacheVersion here? It is never used at the moment. Failover in future?
    private final ConcurrentSkipListMap<Long, GridCacheVersion> activeTxs = new ConcurrentSkipListMap<>();

    /** */
    private final ActiveQueries activeQueries = new ActiveQueries();

    /** */
    private final MvccPreviousCoordinatorQueries prevCrdQueries = new MvccPreviousCoordinatorQueries();

    /** */
    private final ConcurrentMap<Long, MvccSnapshotFuture> snapshotFuts = new ConcurrentHashMap<>();

    /** */
    private final ConcurrentMap<Long, WaitAckFuture> ackFuts = new ConcurrentHashMap<>();

    /** */
    private ConcurrentMap<Long, WaitTxFuture> waitTxFuts = new ConcurrentHashMap<>();

    /** */
    private final AtomicLong futIdCntr = new AtomicLong();

    /** */
    private final CountDownLatch crdLatch = new CountDownLatch(1);

    /** Topology version when local node was assigned as coordinator. */
    private long crdVer;

    /** */
    private MvccDiscoveryData discoData = new MvccDiscoveryData(null);

    /** For tests only. */
    private static IgniteClosure<Collection<ClusterNode>, ClusterNode> crdC;

    /** */
    private TxLog txLog;

    /** */
    private ScheduledExecutorService vacuumScheduledExecutor;

    /** */
    private BlockingQueue<T2<Integer, List<MvccLinkAwareSearchRow>>> cleanupQueue;

    /** */
    private List<VacuumWorker> vacuumWorkers;

    /** */
    private VacuumScheduler vacuumScheduler;

    /** */
    private final AtomicLong lastCleanupVer = new AtomicLong();

    /** */
    private BlockingQueue<VacuumPageLocator> pageIdsQueue;

    /** */
    private static Collection<VacuumListener> vacuumLsnrs = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /** For tests only. */
    private volatile Throwable vacuumError;

    /** For tests only. */ // TODO replace with semaphore.
    private final Object vacuumEndNotifier = new Object();

    /**
     * @param ctx Context.
     */
    public MvccProcessor(GridKernalContext ctx) {
        super(ctx);

        ctx.internalSubscriptionProcessor().registerDatabaseListener(this);

        if (!ctx.clientNode()) {
            vacuumScheduledExecutor = Executors.newSingleThreadScheduledExecutor(
                new ThreadFactory() {
                    @Override public Thread newThread(Runnable r) {
                        Thread thread = new Thread(r, "vacuum-scheduler-node-" + ctx.grid().localNode().order());

                        thread.setDaemon(true);

                        return thread;
                    }
                });

            cleanupQueue = new LinkedBlockingQueue<>(ctx.config().getMvccVacuumCleanupQueueSize());

            vacuumWorkers = new ArrayList<>(ctx.config().getMvccVacuumThreadCnt());

            vacuumScheduler = new VacuumScheduler();

            pageIdsQueue = new LinkedBlockingQueue<>();
        }
    }

    /** {@inheritDoc} */
    @Override public void onKernalStart(boolean active) throws IgniteCheckedException {
        super.onKernalStart(active);

        if (!ctx.clientNode()) {
            for (int i = 0; i < ctx.config().getMvccVacuumThreadCnt(); i++) {
                VacuumWorker vacuumWorker = new VacuumWorker();

                vacuumWorkers.add(vacuumWorker);

                new IgniteThread(vacuumWorker).start();
            }

            int timeInterval = ctx.config().getMvccVacuumTimeInterval();

            vacuumScheduledExecutor.scheduleWithFixedDelay(vacuumScheduler, timeInterval, timeInterval, TimeUnit.MILLISECONDS);
        }
    }

    /** {@inheritDoc} */
    @Override public void onKernalStop(boolean cancel) {
        super.onKernalStop(cancel);

        if (!ctx.clientNode()) {
            U.cancel(vacuumScheduler);
            U.cancel(vacuumWorkers);
            U.join(vacuumWorkers, log);

            U.shutdownNow(MvccProcessor.class, vacuumScheduledExecutor, log);
        }
    }

    /** {@inheritDoc} */
    @Override public void start() throws IgniteCheckedException {
        ctx.event().addLocalEventListener(new CacheCoordinatorNodeFailListener(),
            EVT_NODE_FAILED, EVT_NODE_LEFT);

        ctx.io().addMessageListener(TOPIC_CACHE_COORDINATOR, new CoordinatorMessageListener());
    }

    /** {@inheritDoc} */
    @Override public DiscoveryDataExchangeType discoveryDataType() {
        return DiscoveryDataExchangeType.CACHE_CRD_PROC;
    }

    /** {@inheritDoc} */
    @SuppressWarnings("ConstantConditions")
    @Override public void collectGridNodeData(DiscoveryDataBag dataBag) {
        Integer cmpId = discoveryDataType().ordinal();

        if (!dataBag.commonDataCollectedFor(cmpId))
            dataBag.addGridCommonData(cmpId, discoData);
    }

    /** {@inheritDoc} */
    @Override public void onGridDataReceived(DiscoveryDataBag.GridDiscoveryData data) {
        MvccDiscoveryData discoData0 = (MvccDiscoveryData)data.commonData();

        // Disco data might be null in case the first joined node is daemon.
        if (discoData0 != null) {
            discoData = discoData0;

            log.info("Received mvcc coordinator on node join: " + discoData.coordinator());

            assert discoData != null;
        }
    }

    /**
     * @param ver Version to check.
     * @return State for given mvcc version.
     * @throws IgniteCheckedException If fails.
     */
    public byte state(MvccVersion ver) throws IgniteCheckedException {
        return txLog.get(ver.coordinatorVersion(), ver.counter());
    }

    /**
     * @param ver Version.
     * @param state State.
     * @throws IgniteCheckedException If fails;
     */
    public void updateState(MvccVersion ver, byte state) throws IgniteCheckedException {
        txLog.put(ver.coordinatorVersion(), ver.counter(), state);
    }

    /**
     * Removes all less or equals to the given one records from Tx log.
     * @param ver Version.
     * @throws IgniteCheckedException If fails.
     */
    public void removeUntil(MvccVersion ver) throws IgniteCheckedException {
        txLog.removeUntil(ver.coordinatorVersion(), ver.counter());
    }

    /**
     * @return Discovery data.
     */
    public MvccDiscoveryData discoveryData() {
        return discoData;
    }

    /**
     * For testing only.
     *
     * @param crdC Closure assigning coordinator.
     */
    static void coordinatorAssignClosure(IgniteClosure<Collection<ClusterNode>, ClusterNode> crdC) {
        MvccProcessor.crdC = crdC;
    }

    /**
     * @param evtType Event type.
     * @param nodes Current nodes.
     * @param topVer Topology version.
     */
    public void onDiscoveryEvent(int evtType, Collection<ClusterNode> nodes, long topVer) {
        if (evtType == EVT_NODE_METRICS_UPDATED || evtType == EVT_DISCOVERY_CUSTOM_EVT)
            return;

        MvccCoordinator crd;

        if (evtType == EVT_NODE_SEGMENTED || evtType == EVT_CLIENT_NODE_DISCONNECTED)
            crd = null;
        else {
            crd = discoData.coordinator();

            if (crd == null ||
                ((evtType == EVT_NODE_FAILED || evtType == EVT_NODE_LEFT) && !F.nodeIds(nodes).contains(crd.nodeId()))) {
                ClusterNode crdNode = null;

                if (crdC != null) {
                    crdNode = crdC.apply(nodes);

                    if (log.isInfoEnabled())
                        log.info("Assigned coordinator using test closure: " + crd);
                }
                else {
                    // Expect nodes are sorted by order.
                    for (ClusterNode node : nodes) {
                        if (!CU.clientNode(node)) {
                            crdNode = node;

                            break;
                        }
                    }
                }

                crd = crdNode != null ? new MvccCoordinator(crdNode.id(), coordinatorVersion(topVer), new AffinityTopologyVersion(topVer, 0)) : null;

                if (crd != null) {
                    if (log.isInfoEnabled())
                        log.info("Assigned mvcc coordinator [crd=" + crd + ", crdNode=" + crdNode + ']');
                }
                else
                    U.warn(log, "New mvcc coordinator was not assigned [topVer=" + topVer + ']');
            }
        }

        discoData = new MvccDiscoveryData(crd);
    }

    /**
     * @param topVer Topology version.
     * @return Coordinator version.
     */
    private long coordinatorVersion(long topVer) {
        return topVer + ctx.discovery().gridStartTime();
    }

    /**
     * @param tx Transaction.
     * @return Counter.
     */
    public MvccSnapshot requestTxSnapshotOnCoordinator(IgniteInternalTx tx) {
        assert ctx.localNodeId().equals(currentCoordinatorId());

        return assignTxSnapshot(tx.nearXidVersion(), 0L);
    }

    /**
     * @param ver Version.
     * @return Counter.
     */
    public MvccSnapshot requestTxSnapshotOnCoordinator(GridCacheVersion ver) {
        assert ctx.localNodeId().equals(currentCoordinatorId());

        return assignTxSnapshot(ver, 0L);
    }

    /**
     * @param crd Coordinator.
     * @param lsnr Response listener.
     * @param txVer Transaction version.
     * @return Counter request future.
     */
    public IgniteInternalFuture<MvccSnapshot> requestTxSnapshot(MvccCoordinator crd,
        MvccSnapshotResponseListener lsnr,
        GridCacheVersion txVer) {
        assert !ctx.localNodeId().equals(crd.nodeId());

        MvccSnapshotFuture fut = new MvccSnapshotFuture(futIdCntr.incrementAndGet(), crd, lsnr);

        snapshotFuts.put(fut.id, fut);

        try {
            sendMessage(crd.nodeId(), new MvccTxSnapshotRequest(fut.id, txVer));
        }
        catch (IgniteCheckedException e) {
            if (snapshotFuts.remove(fut.id) != null)
                fut.onError(e);
        }

        return fut;
    }

    /**
     * @param crd Coordinator.
     * @param mvccVer Query version.
     */
    public void ackQueryDone(MvccCoordinator crd, MvccSnapshot mvccVer) {
        assert crd != null;

        long trackCntr = queryTrackCounter(mvccVer);

        Message msg = crd.coordinatorVersion() == mvccVer.coordinatorVersion() ? new MvccAckRequestQuery(trackCntr) :
            new MvccNewQueryAckRequest(mvccVer.coordinatorVersion(), trackCntr);

        try {
            sendMessage(crd.nodeId(), msg);
        }
        catch (ClusterTopologyCheckedException e) {
            if (log.isDebugEnabled())
                log.debug("Failed to send query ack, node left [crd=" + crd + ", msg=" + msg + ']');
        }
        catch (IgniteCheckedException e) {
            U.error(log, "Failed to send query ack [crd=" + crd + ", msg=" + msg + ']', e);
        }
    }

    /**
     * @param mvccVer Read version.
     * @return Tracker counter.
     */
    private long queryTrackCounter(MvccSnapshot mvccVer) {
        long trackCntr = mvccVer.counter();

        MvccLongList txs = mvccVer.activeTransactions();

        int size = txs.size();

        for (int i = 0; i < size; i++) {
            long txId = txs.get(i);

            if (txId < trackCntr)
                trackCntr = txId;
        }

        return trackCntr;
    }

    /**
     * @param crd Coordinator.
     * @return Counter request future.
     */
    public IgniteInternalFuture<MvccSnapshot> requestQuerySnapshot(MvccCoordinator crd) {
        assert crd != null;

        MvccSnapshotFuture fut = new MvccSnapshotFuture(futIdCntr.incrementAndGet(), crd, null);

        snapshotFuts.put(fut.id, fut);

        try {
            sendMessage(crd.nodeId(), new MvccQuerySnapshotRequest(fut.id));
        }
        catch (IgniteCheckedException e) {
            if (snapshotFuts.remove(fut.id) != null)
                fut.onError(e);
        }

        return fut;
    }

    /**
     * @param crdId Coordinator ID.
     * @param txs Transaction IDs.
     * @return Future.
     */
    public IgniteInternalFuture<Void> waitTxsFuture(UUID crdId, GridLongList txs) {
        assert crdId != null;
        assert txs != null && txs.size() > 0;

        WaitAckFuture fut = new WaitAckFuture(futIdCntr.incrementAndGet(), crdId, false);

        ackFuts.put(fut.id, fut);

        try {
            sendMessage(crdId, new MvccWaitTxsRequest(fut.id, txs));
        }
        catch (IgniteCheckedException e) {
            if (ackFuts.remove(fut.id) != null) {
                if (e instanceof ClusterTopologyCheckedException)
                    fut.onDone(); // No need to wait, new coordinator will be assigned, finish without error.
                else
                    fut.onDone(e);
            }
        }

        return fut;
    }

    /**
     * @param crd Coordinator.
     * @param updateVer Transaction update version.
     * @param readVer Transaction read version.
     * @return Acknowledge future.
     */
    public IgniteInternalFuture<Void> ackTxCommit(UUID crd,
        MvccSnapshot updateVer,
        @Nullable MvccSnapshot readVer) {
        assert crd != null;
        assert updateVer != null;

        WaitAckFuture fut = new WaitAckFuture(futIdCntr.incrementAndGet(), crd, true);

        ackFuts.put(fut.id, fut);

        MvccAckRequestTx msg = createTxAckMessage(fut.id, updateVer, readVer);

        try {
            sendMessage(crd, msg);
        }
        catch (IgniteCheckedException e) {
            if (ackFuts.remove(fut.id) != null) {
                if (e instanceof ClusterTopologyCheckedException)
                    fut.onDone(); // No need to ack, finish without error.
                else
                    fut.onDone(e);
            }
        }

        return fut;
    }

    /**
     * @param futId Future ID.
     * @param updateVer Update version.
     * @param readVer Optional read version.
     * @return Message.
     */
    private MvccAckRequestTx createTxAckMessage(long futId,
        MvccSnapshot updateVer,
        @Nullable MvccSnapshot readVer) {
        MvccAckRequestTx msg;

        if (readVer != null) {
            long trackCntr = queryTrackCounter(readVer);

            if (readVer.coordinatorVersion() == updateVer.coordinatorVersion()) {
                msg = new MvccAckRequestTxAndQuery(futId,
                    updateVer.counter(),
                    trackCntr);
            }
            else {
                msg = new MvccAckRequestTxAndQueryEx(futId,
                    updateVer.counter(),
                    readVer.coordinatorVersion(),
                    trackCntr);
            }
        }
        else
            msg = new MvccAckRequestTx(futId, updateVer.counter());

        return msg;
    }

    /**
     * @param crdId Coordinator node ID.
     * @param updateVer Transaction update version.
     * @param readVer Transaction read version.
     */
    public void ackTxRollback(UUID crdId, MvccSnapshot updateVer, @Nullable MvccSnapshot readVer) {
        MvccAckRequestTx msg = createTxAckMessage(0, updateVer, readVer);

        msg.skipResponse(true);

        try {
            sendMessage(crdId, msg);
        }
        catch (ClusterTopologyCheckedException e) {
            if (log.isDebugEnabled())
                log.debug("Failed to send tx rollback ack, node left [msg=" + msg + ", node=" + crdId + ']');
        }
        catch (IgniteCheckedException e) {
            U.error(log, "Failed to send tx rollback ack [msg=" + msg + ", node=" + crdId + ']', e);
        }
    }

    /**
     * @param nodeId Sender node ID.
     * @param msg Message.
     */
    private void processCoordinatorTxSnapshotRequest(UUID nodeId, MvccTxSnapshotRequest msg) {
        ClusterNode node = ctx.discovery().node(nodeId);

        if (node == null) {
            if (log.isDebugEnabled())
                log.debug("Ignore tx snapshot request processing, node left [msg=" + msg + ", node=" + nodeId + ']');

            return;
        }

        MvccSnapshotResponse res = assignTxSnapshot(msg.txId(), msg.futureId());

        try {
            sendMessage(node.id(), res);
        }
        catch (ClusterTopologyCheckedException e) {
            if (log.isDebugEnabled())
                log.debug("Failed to send tx snapshot response, node left [msg=" + msg + ", node=" + nodeId + ']');
        }
        catch (IgniteCheckedException e) {
            U.error(log, "Failed to send tx snapshot response [msg=" + msg + ", node=" + nodeId + ']', e);
        }
    }

    /**
     * @param nodeId Sender node ID.
     * @param msg Message.
     */
    private void processCoordinatorQuerySnapshotRequest(UUID nodeId, MvccQuerySnapshotRequest msg) {
        ClusterNode node = ctx.discovery().node(nodeId);

        if (node == null) {
            if (log.isDebugEnabled())
                log.debug("Ignore query counter request processing, node left [msg=" + msg + ", node=" + nodeId + ']');

            return;
        }

        MvccSnapshotResponse res = assignQueryCounter(nodeId, msg.futureId());

        try {
            sendMessage(node.id(), res);
        }
        catch (ClusterTopologyCheckedException e) {
            if (log.isDebugEnabled())
                log.debug("Failed to send query counter response, node left [msg=" + msg + ", node=" + nodeId + ']');
        }
        catch (IgniteCheckedException e) {
            U.error(log, "Failed to send query counter response [msg=" + msg + ", node=" + nodeId + ']', e);

            onQueryDone(nodeId, res.counter());
        }
    }

    /**
     * @param nodeId Sender node ID.
     * @param msg Message.
     */
    private void processCoordinatorSnapshotResponse(UUID nodeId, MvccSnapshotResponse msg) {
        MvccSnapshotFuture fut = snapshotFuts.remove(msg.futureId());

        if (fut != null) {
            fut.onResponse(msg);
        }
        else {
            if (ctx.discovery().alive(nodeId))
                U.warn(log, "Failed to find query version future [node=" + nodeId + ", msg=" + msg + ']');
            else if (log.isDebugEnabled())
                log.debug("Failed to find query version future [node=" + nodeId + ", msg=" + msg + ']');
        }
    }

    /**
     * @param nodeId Node ID.
     * @param msg Message.
     */
    private void processCoordinatorQueryAckRequest(UUID nodeId, MvccAckRequestQuery msg) {
        onQueryDone(nodeId, msg.counter());
    }

    /**
     * @param nodeId Node ID.
     * @param msg Message.
     */
    private void processNewCoordinatorQueryAckRequest(UUID nodeId, MvccNewQueryAckRequest msg) {
        prevCrdQueries.onQueryDone(nodeId, msg.coordinatorVersion(), msg.counter());
    }

    /**
     * @param nodeId Sender node ID.
     * @param msg Message.
     */
    private void processCoordinatorTxAckRequest(UUID nodeId, MvccAckRequestTx msg) {
        onTxDone(msg.txCounter());

        if (msg.queryCounter() != MVCC_COUNTER_NA) {
            if (msg.queryCoordinatorVersion() == 0)
                onQueryDone(nodeId, msg.queryCounter());
            else
                prevCrdQueries.onQueryDone(nodeId, msg.queryCoordinatorVersion(), msg.queryCounter());
        }

        if (!msg.skipResponse()) {
            try {
                sendMessage(nodeId, new MvccFutureResponse(msg.futureId()));
            }
            catch (ClusterTopologyCheckedException e) {
                if (log.isDebugEnabled())
                    log.debug("Failed to send tx ack response, node left [msg=" + msg + ", node=" + nodeId + ']');
            }
            catch (IgniteCheckedException e) {
                U.error(log, "Failed to send tx ack response [msg=" + msg + ", node=" + nodeId + ']', e);
            }
        }
    }

    /**
     * @param nodeId Sender node ID.
     * @param msg Message.
     */
    private void processCoordinatorAckResponse(UUID nodeId, MvccFutureResponse msg) {
        WaitAckFuture fut = ackFuts.remove(msg.futureId());

        if (fut != null) {
            fut.onResponse();
        }
        else {
            if (ctx.discovery().alive(nodeId))
                U.warn(log, "Failed to find tx ack future [node=" + nodeId + ", msg=" + msg + ']');
            else if (log.isDebugEnabled())
                log.debug("Failed to find tx ack future [node=" + nodeId + ", msg=" + msg + ']');
        }
    }

    /**
     * @return Cleanup version.
     */
    private long getCleanupVersion() {
        long minActive = Long.MAX_VALUE;

        for (Long txVer : activeTxs.keySet()) {
            if (txVer < minActive)
                minActive = txVer;
        }

        long cleanupVer;

        if (prevCrdQueries.previousQueriesDone()) {
            cleanupVer = Math.min(minActive, committedCntr.get());

            cleanupVer--;

            Long qryVer = activeQueries.minimalQueryCounter();

            if (qryVer != null && qryVer <= cleanupVer)
                cleanupVer = qryVer - 1;
        }
        else
            cleanupVer = -1;

        return cleanupVer;
    }

    /**
     * @param txId Transaction ID.
     * @return Counter.
     */
    private MvccSnapshotResponse assignTxSnapshot(GridCacheVersion txId, long futId) {
        assert crdVer != 0;

        // TODO: Race
        long nextCtr = mvccCntr.incrementAndGet();

        MvccSnapshotResponse res = new MvccSnapshotResponse();

        long minActive = Long.MAX_VALUE;

        for (Long txVer : activeTxs.keySet()) {
            if (txVer < minActive)
                minActive = txVer;

            res.addTx(txVer);
        }

        Object old = activeTxs.put(nextCtr, txId);

        assert old == null : txId;

        long cleanupVer;

        if (prevCrdQueries.previousQueriesDone()) {
            cleanupVer = Math.min(minActive, committedCntr.get());

            cleanupVer--;

            Long qryVer = activeQueries.minimalQueryCounter();

            if (qryVer != null && qryVer <= cleanupVer)
                cleanupVer = qryVer - 1;
        }
        else
            cleanupVer = -1;

        res.init(futId, crdVer, nextCtr, cleanupVer);

        updateLastCleanupVersion(res.cleanupVersion());

        return res;
    }

    /**
     * @param txCntr Counter assigned to transaction.
     */
    private void onTxDone(Long txCntr) {
        GridFutureAdapter fut;

        GridCacheVersion ver = activeTxs.remove(txCntr);

        assert ver != null;

        committedCntr.setIfGreater(txCntr);

        fut = waitTxFuts.remove(txCntr);

        if (fut != null)
            fut.onDone();
    }

    /** {@inheritDoc} */
    @Override public void onInitDataRegions(IgniteCacheDatabaseSharedManager mgr) throws IgniteCheckedException {
        DataStorageConfiguration dscfg = dataStorageConfiguration();

        mgr.addDataRegion(
            dscfg,
            createTxLogRegion(dscfg),
            CU.isPersistenceEnabled(ctx.config()));
    }

    /** {@inheritDoc} */
    @Override public void afterInitialise(IgniteCacheDatabaseSharedManager mgr) throws IgniteCheckedException {
        if (!CU.isPersistenceEnabled(ctx.config())) {
            assert txLog == null;

            txLog = new TxLog(ctx, mgr);
        }
    }

    /** {@inheritDoc} */
    @Override public void beforeMemoryRestore(IgniteCacheDatabaseSharedManager mgr) throws IgniteCheckedException {
        assert CU.isPersistenceEnabled(ctx.config());
        assert txLog == null;

        ctx.cache().context().pageStore().initialize(TX_LOG_CACHE_ID, 1,
            TX_LOG_CACHE_NAME, mgr.dataRegion(TX_LOG_CACHE_NAME).memoryMetrics());
    }

    /** {@inheritDoc} */
    @Override public void afterMemoryRestore(IgniteCacheDatabaseSharedManager mgr) throws IgniteCheckedException {
        assert CU.isPersistenceEnabled(ctx.config());
        assert txLog == null;

        txLog = new TxLog(ctx, mgr);
    }

    /** {@inheritDoc} */
    @Override public void beforeStop(IgniteCacheDatabaseSharedManager mgr) {
        txLog = null;
    }

    /**
     * TODO IGNITE-7966
     * @return Data region configuration.
     */
    private DataRegionConfiguration createTxLogRegion(DataStorageConfiguration dscfg) {
        DataRegionConfiguration cfg = new DataRegionConfiguration();

        cfg.setName(TX_LOG_CACHE_NAME);
        cfg.setInitialSize(dscfg.getSystemRegionInitialSize());
        cfg.setMaxSize(dscfg.getSystemRegionMaxSize());
        cfg.setPersistenceEnabled(CU.isPersistenceEnabled(dscfg));
        return cfg;
    }

    /**
     *
     * @return Data storage configuration.
     */
    private DataStorageConfiguration dataStorageConfiguration() {
        return ctx.config().getDataStorageConfiguration();
    }

    /**
     *
     */
    class ActiveQueries {
        /** */
        private final Map<UUID, TreeMap<Long, AtomicInteger>> activeQueries = new HashMap<>();

        /** */
        private Long minQry;

        Long minimalQueryCounter() {
            synchronized (this) {
                return minQry;
            }
        }

        synchronized MvccSnapshotResponse assignQueryCounter(UUID nodeId, long futId) {
            MvccSnapshotResponse res = new MvccSnapshotResponse();

            Long mvccCntr;
            Long trackCntr;

            for (; ; ) {
                mvccCntr = committedCntr.get();

                trackCntr = mvccCntr;

                for (Long txVer : activeTxs.keySet()) {
                    if (txVer < trackCntr)
                        trackCntr = txVer;

                    res.addTx(txVer);
                }

                Long minQry0 = minQry;

                if (minQry == null || trackCntr < minQry)
                    minQry = trackCntr;

                if (committedCntr.get() == mvccCntr)
                    break;

                minQry = minQry0;

                res.resetTransactionsCount();
            }

            TreeMap<Long, AtomicInteger> nodeMap = activeQueries.get(nodeId);

            if (nodeMap == null)
                activeQueries.put(nodeId, nodeMap = new TreeMap<>());

            AtomicInteger qryCnt = nodeMap.get(trackCntr);

            if (qryCnt == null)
                nodeMap.put(trackCntr, new AtomicInteger(1));
            else
                qryCnt.incrementAndGet();

            res.init(futId, crdVer, mvccCntr, MVCC_COUNTER_NA);

            return res;
        }

        synchronized void onQueryDone(UUID nodeId, Long mvccCntr) {
            TreeMap<Long, AtomicInteger> nodeMap = activeQueries.get(nodeId);

            if (nodeMap == null)
                return;

            assert minQry != null;

            AtomicInteger qryCnt = nodeMap.get(mvccCntr);

            assert qryCnt != null : "[node=" + nodeId + ", nodeMap=" + nodeMap + ", cntr=" + mvccCntr + "]";

            int left = qryCnt.decrementAndGet();

            if (left == 0) {
                nodeMap.remove(mvccCntr);

                if (mvccCntr == minQry.longValue())
                    minQry = activeMinimal();
            }
        }

        synchronized void onNodeFailed(UUID nodeId) {
            activeQueries.remove(nodeId);

            minQry = activeMinimal();
        }

        private Long activeMinimal() {
            Long min = null;

            for (TreeMap<Long, AtomicInteger> m : activeQueries.values()) {
                Map.Entry<Long, AtomicInteger> e = m.firstEntry();

                if (e != null && (min == null || e.getKey() < min))
                    min = e.getKey();
            }

            return min;
        }
    }

    /**
     * @param qryNodeId Node initiated query.
     * @return Counter for query.
     */
    private MvccSnapshotResponse assignQueryCounter(UUID qryNodeId, long futId) {
        assert crdVer != 0;

        MvccSnapshotResponse res = activeQueries.assignQueryCounter(qryNodeId, futId);

        return res;

        // TODO: Dead code?
//        MvccSnapshotResponse res = new MvccSnapshotResponse();
//
//        Long mvccCntr;
//
//        for(;;) {
//            mvccCntr = committedCntr.get();
//
//            Long trackCntr = mvccCntr;
//
//            for (Long txVer : activeTxs.keySet()) {
//                if (txVer < trackCntr)
//                    trackCntr = txVer;
//
//                res.addTx(txVer);
//            }
//
//            registerActiveQuery(trackCntr);
//
//            if (committedCntr.get() == mvccCntr)
//                break;
//            else {
//                res.resetTransactionsCount();
//
//                onQueryDone(trackCntr);
//            }
//        }
//
//        res.init(futId, crdVer, mvccCntr, MVCC_COUNTER_NA);
//
//        return res;
    }
//
//    private void registerActiveQuery(Long mvccCntr) {
//        for (;;) {
//            AtomicInteger qryCnt = activeQueries.get(mvccCntr);
//
//            if (qryCnt != null) {
//                boolean inc = increment(qryCnt);
//
//                if (!inc) {
//                    activeQueries.remove(mvccCntr, qryCnt);
//
//                    continue;
//                }
//            }
//            else {
//                qryCnt = new AtomicInteger(1);
//
//                if (activeQueries.putIfAbsent(mvccCntr, qryCnt) != null)
//                    continue;
//            }
//
//            break;
//        }
//    }
//
//    static boolean increment(AtomicInteger cntr) {
//        for (;;) {
//            int current = cntr.get();
//
//            if (current == 0)
//                return false;
//
//            if (cntr.compareAndSet(current, current + 1))
//                return true;
//        }
//    }

    /**
     * @param mvccCntr Query counter.
     */
    private void onQueryDone(UUID nodeId, Long mvccCntr) {
        activeQueries.onQueryDone(nodeId, mvccCntr);

        // TODO: Dead code?
//        AtomicInteger qryCnt = activeQueries.get(mvccCntr);
//
//        assert qryCnt != null : mvccCntr;
//
//        int left = qryCnt.decrementAndGet();
//
//        assert left >= 0 : left;
//
//        if (left == 0)
//            activeQueries.remove(mvccCntr, qryCnt);
    }

    /**
     * @param nodeId Node ID.
     * @param msg Message.
     */
    @SuppressWarnings("unchecked")
    private void processCoordinatorWaitTxsRequest(final UUID nodeId, final MvccWaitTxsRequest msg) {
        GridLongList txs = msg.transactions();

        GridCompoundFuture resFut = null;

        for (int i = 0; i < txs.size(); i++) {
            Long txId = txs.get(i);

            WaitTxFuture fut = waitTxFuts.get(txId);

            if (fut == null) {
                WaitTxFuture old = waitTxFuts.putIfAbsent(txId, fut = new WaitTxFuture(txId));

                if (old != null)
                    fut = old;
            }

            if (!activeTxs.containsKey(txId))
                fut.onDone();

            if (!fut.isDone()) {
                if (resFut == null)
                    resFut = new GridCompoundFuture();

                resFut.add(fut);
            }
        }

        if (resFut != null)
            resFut.markInitialized();

        if (resFut == null || resFut.isDone())
            sendFutureResponse(nodeId, msg);
        else {
            resFut.listen(new IgniteInClosure<IgniteInternalFuture>() {
                @Override public void apply(IgniteInternalFuture fut) {
                    sendFutureResponse(nodeId, msg);
                }
            });
        }
    }

    /**
     * @param nodeId Node ID.
     * @param msg Message.
     */
    private void sendFutureResponse(UUID nodeId, MvccWaitTxsRequest msg) {
        try {
            sendMessage(nodeId, new MvccFutureResponse(msg.futureId()));
        }
        catch (ClusterTopologyCheckedException e) {
            if (log.isDebugEnabled())
                log.debug("Failed to send tx ack response, node left [msg=" + msg + ", node=" + nodeId + ']');
        }
        catch (IgniteCheckedException e) {
            U.error(log, "Failed to send tx ack response [msg=" + msg + ", node=" + nodeId + ']', e);
        }
    }

    /**
     * @return Coordinator.
     */
    public MvccCoordinator currentCoordinator() {
        return curCrd;
    }

    /**
     * @param curCrd Coordinator.
     */
    public void currentCoordinator(MvccCoordinator curCrd) {
        this.curCrd = curCrd;
    }

    /**
     * @return Current coordinator node ID.
     */
    public UUID currentCoordinatorId() {
        MvccCoordinator curCrd = this.curCrd;

        return curCrd != null ? curCrd.nodeId() : null;
    }

    /**
     * @param topVer Cache affinity version (used for assert).
     * @return Coordinator.
     */
    public MvccCoordinator currentCoordinatorForCacheAffinity(AffinityTopologyVersion topVer) {
        MvccCoordinator crd = curCrd;

        // Assert coordinator did not already change.
        assert crd == null || crd.topologyVersion().compareTo(topVer) <= 0 :
            "Invalid coordinator [crd=" + crd + ", topVer=" + topVer + ']';

        return crd;
    }

    /**
     * @param nodeId Node ID
     * @param activeQueries Active queries.
     */
    public void processClientActiveQueries(UUID nodeId,
        @Nullable Map<MvccVersion, Integer> activeQueries) {
        prevCrdQueries.addNodeActiveQueries(nodeId, activeQueries);
    }

    /**
     * @param nodeId Node ID.
     * @param msg Message.
     */
    private void processCoordinatorActiveQueriesMessage(UUID nodeId, MvccActiveQueriesMessage msg) {
        prevCrdQueries.addNodeActiveQueries(nodeId, msg.activeQueries());
    }

    /**
     * @param nodeId Coordinator node ID.
     * @param activeQueries Active queries.
     */
    public void sendActiveQueries(UUID nodeId, @Nullable Map<MvccVersion, Integer> activeQueries) {
        MvccActiveQueriesMessage msg = new MvccActiveQueriesMessage(activeQueries);

        try {
            sendMessage(nodeId, msg);
        }
        catch (IgniteCheckedException e) {
            U.error(log, "Failed to send active queries to mvcc coordinator: " + e);
        }
    }

    /**
     * @param topVer Topology version.
     * @param discoCache Discovery data.
     * @param activeQueries Current queries.
     */
    public void initCoordinator(AffinityTopologyVersion topVer,
        DiscoCache discoCache,
        Map<UUID, Map<MvccVersion, Integer>> activeQueries) {
        assert ctx.localNodeId().equals(curCrd.nodeId());

        MvccCoordinator crd = discoCache.mvccCoordinator();

        assert crd != null;

        // No need to re-initialize if coordinator version hasn't changed (e.g. it was cluster activation).
        if (crdVer == crd.coordinatorVersion())
            return;

        crdVer = crd.coordinatorVersion();

        log.info("Initialize local node as mvcc coordinator [node=" + ctx.localNodeId() +
            ", topVer=" + topVer +
            ", crdVer=" + crdVer + ']');

        prevCrdQueries.init(activeQueries, discoCache, ctx.discovery());

        crdLatch.countDown();
    }

    /**
     * Send IO message.
     *
     * @param nodeId Node ID.
     * @param msg Message.
     */
    private void sendMessage(UUID nodeId, Message msg) throws IgniteCheckedException {
        ctx.io().sendToGridTopic(nodeId, TOPIC_CACHE_COORDINATOR, msg, SYSTEM_POOL);
    }

    /**
     * @param lsnr Vacuum listener.
     */
    public void addVacuumListener(VacuumListener lsnr) {

        vacuumLsnrs.add(lsnr);

        System.out.println("add vacuum listener " + lsnr + "; lsnr.size="  + vacuumLsnrs.size());
    }

    /**
     * @param lsnr Vacuum listener.
     */
    public void removeVacuumListener(VacuumListener lsnr) {
        vacuumLsnrs.remove(lsnr);
    }

    /**
     * Runs vacuum process.
     */
    public void runVacuum() throws Exception {
        System.out.println("Run vacuum!!");

        if (log.isDebugEnabled())
            log.debug("Vacuum started.");

        long startTime = System.currentTimeMillis();

        AtomicInteger pagesCnt = new AtomicInteger();
        AtomicLong entriesCnt = new AtomicLong();

        IgniteCacheDatabaseSharedManager db = ctx.cache().context().database();

//        assert pageIdsQueue.isEmpty();

        for (VacuumListener lsnr : vacuumLsnrs)
            lsnr.onVacuumStart(pageIdsQueue);

        VacuumPageLocator pageLocator;

        final Map<Integer, List<MvccLinkAwareSearchRow>> cleanupMap = new HashMap<>();

        while ((pageLocator = pageIdsQueue.poll()) != null) {
            PageMemory pageMem;

            if (pageLocator.inMemory())
                pageMem = pageLocator.pageMem();
            else {
                int grpId = pageLocator.groupId();

                assert grpId != 0;

                CacheGroupContext grpCtx = ctx.cache().cacheGroup(grpId);

                assert grpCtx != null;

                pageMem = grpCtx.dataRegion().pageMemory();
            }

            assert pageMem != null;

            long pageId = pageLocator.pageId();
            int grpId = pageLocator.groupId();

            System.out.println("acquirePage pageId=" + pageId +"; partId=" + PageIdUtils.partId(pageId) + "; pageIdx=" + PageIdUtils.pageIndex(pageId));
            long page = pageMem.acquirePage(grpId, pageId);

            try {
                long pageAddr = pageMem.readLockForce(grpId, pageId, page);

                if (pageAddr == 0L) {
                    System.out.println("LOCK FAILED@!!!!!!!");

                    continue;
                }

                try {
                    PageIO io = null;

                    try {
                        io = PageIO.getPageIO(pageAddr);

                        System.out.println("page idx=" + PageIdUtils.pageIndex(pageId)  + "; io=" + io);
                    }

                    catch (Exception e) {
                        System.out.println("ERROR visiting pageLocator=" + pageLocator + "; pageAddr=" + pageAddr);

                        throw e;
                    }


                    //System.out.println( "vacuum go to pageLocator=" + pageLocator + "; PageIO=" + io.getClass());

                    if (io.getType() == T_DATA) {
                        DataPageIO iox = (DataPageIO)io;

                        List<MvccLinkAwareSearchRow> cleanupRowsList = iox.forAllItemsWithNormalLinks(pageAddr,
                                new MvccCleanupFilterClosure(pageMem.pageSize(), iox, pageAddr));

                       // System.out.println("cleanupRowsList=" + cleanupRowsList);

                        for (MvccLinkAwareSearchRow row : cleanupRowsList) {
                            if (row != null) {
                                int cacheId = row.cacheId();

                                List<MvccLinkAwareSearchRow> cleanupRows = cleanupMap.get(cacheId);

                                if (cleanupRows == null)
                                    cleanupMap.put(cacheId, cleanupRows = new ArrayList<>());

                                cleanupRows.add(row);

                                entriesCnt.incrementAndGet();
                            }
                        }

                        //Submit dead entries to the cleanup workers queue.
                        for (Iterator<Map.Entry<Integer, List<MvccLinkAwareSearchRow>>> it =
                            cleanupMap.entrySet().iterator(); it.hasNext(); ) {
                            Map.Entry<Integer, List<MvccLinkAwareSearchRow>> entry = it.next();

                            if (entry.getValue().size() > ctx.config().getMvccVacuumCleanupBatchSize()) {

                                if (!submitVacuumTask(entry.getKey(), entry.getValue()))
                                    return;

                                it.remove();
                            }
                        }
                    }
                }
                finally {
                    pageMem.readUnlock(grpId, pageId, page);
                }
            }
            finally {
                pageMem.releasePage(grpId, pageId, page);
            }
        }

        // Submit the rest of dead entries to the cleanup worker.
        for (Map.Entry<Integer, List<MvccLinkAwareSearchRow>> entry : cleanupMap.entrySet()) {
            if (!submitVacuumTask(entry.getKey(), entry.getValue()))
                return;
        }



//        int rc = 0;
//
//        for (Map.Entry<Integer, List<MvccLinkAwareSearchRow>> e : cleanupMap.entrySet()) {
//            rc += e.getValue().size();
//        }
//
//        System.out.println("collected " + rc + " links");
//
//        pageIdsQueue.clear();

//        // TODO Interruption
//        for (DataRegion region : db.dataRegions()) {
//            if (db.systemDateRegionName().equals(region.config().getName()))
//                continue;
//
//            PageMemory pageMem = region.pageMemory();
//
//            PageVisitor visitor = null;
//
//            final int pageSize = pageMem.pageSize();
//
//            final Map<Integer, List<MvccLinkAwareSearchRow>> cleanupMap = new HashMap<>();
//
//            visitor.visit(new PageClosure() {
//                @Override public void apply(long pageAddr) throws IgniteCheckedException {
//                    PageIO io = getPageIO(pageAddr);
//
//                    //System.out.println("PageIO=" + io.getClass());
//                    if (io.getType() == T_DATA) { // TODO Is it a MVCC page/entry? We need to know it somehow.
//                        DataPageIO iox = (DataPageIO)io;
//
//                        try {
//                            List<MvccLinkAwareSearchRow> cleanupRowsList = iox.forAllItemsWithNormalLinks(pageAddr,
//                                new MvccCleanupFilterClosure(pageSize, iox, pageAddr));
//
//                            for (MvccLinkAwareSearchRow row : cleanupRowsList) {
//                                if (row != null) {
//                                    int cacheId = row.cacheId();
//
//                                    List<MvccLinkAwareSearchRow> cleanupRows = cleanupMap.get(cacheId);
//
//                                    if (cleanupRows == null)
//                                        cleanupMap.put(cacheId, cleanupRows = new ArrayList<>());
//
//                                    cleanupRows.add(row);
//
//                                    entriesCnt.incrementAndGet();
//                                }
//                            }
//                        }
//                        catch (Error e) {
//                            vacuumError = e;
//
//                            throw e;
//                        }
//
//                        // Submit dead entries to the cleanup workers queue.
//                        for (Iterator<Map.Entry<Integer, List<MvccLinkAwareSearchRow>>> it =
//                            cleanupMap.entrySet().iterator(); it.hasNext(); ) {
//                            Map.Entry<Integer, List<MvccLinkAwareSearchRow>> entry = it.next();
//
//                            if (entry.getValue().size() > ctx.config().getMvccVacuumCleanupBatchSize()) {
//
//                                if (!submitVacuumTask(entry.getKey(), entry.getValue()))
//                                    return;
//
//                                it.remove();
//                            }
//                        }
//                    }
//
//                    pagesCnt.incrementAndGet();
//                }
//            });
//
//            // Submit the rest of dead entries to the cleanup worker.
//            for (Map.Entry<Integer, List<MvccLinkAwareSearchRow>> entry : cleanupMap.entrySet()) {
//                if (!submitVacuumTask(entry.getKey(), entry.getValue()))
//                    return;
//            }
//
//        }
//
//        long pageScanFinish = System.currentTimeMillis();
//
//        // TODO should we wait until cleanupQueue is empty?
//
//        synchronized (cleanupQueue) {
//            while (!cleanupQueue.isEmpty() && !Thread.currentThread().isInterrupted())
//                cleanupQueue.wait();
//        }
//
//        long vacuumFinishTime = System.currentTimeMillis();
//
//        if (log.isInfoEnabled())
//            log.info("Collected " + entriesCnt.get() + " links through " + pagesCnt.get() + " data pages took " +
//                (pageScanFinish - startTime) + " ms." + " Vacuum took " + (vacuumFinishTime - startTime) + " ms. " );
    }

    /**
     * @param cacheId Cache id.
     * @param cleanupRows Cleanup rows list.
     * @return {@code True} if task successfully submitted. {@code false} if interrupted while trying to submit.
     */
    private boolean submitVacuumTask(int cacheId, List<MvccLinkAwareSearchRow> cleanupRows) {
        if (log.isDebugEnabled())
            log.debug("Sent batch for cleaning [cacheId=" + cacheId + ", entries size=" +
                cleanupRows.size() + ']');

        try {
            cleanupQueue.put(new T2<>(cacheId, cleanupRows));
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            log.info("Has been interrupted when trying to submit a cleanup task.");

            return false;
        }

        return true;
    }

    /**
     *
     */
    private class MvccCleanupFilterClosure implements AbstractDataPageIO.CC<MvccLinkAwareSearchRow> {
        /** */
        private final int pageSize;

        /** */
        private final DataPageIO io;

        /** */
        private final long pageAddr;

        /**
         * @param pageSize Page size.
         * @param io Page IO.
         * @param pageAddr Page address.
         */
        public MvccCleanupFilterClosure(int pageSize, DataPageIO io, long pageAddr) {
            this.pageSize = pageSize;
            this.io = io;
            this.pageAddr = pageAddr;
        }

        /** {@inheritDoc} */
        @Override public MvccLinkAwareSearchRow apply(long link) throws IgniteCheckedException {
            int itemId = PageIdUtils.itemId(link);

            if (!io.isFirstChunk(pageAddr, itemId, pageSize))
                return null;

            MvccVersion newVer = io.newMvccVersion(pageAddr, itemId, pageSize);

            if (newVer != null && (newVer.coordinatorVersion() < crdVer ||
                    (newVer.coordinatorVersion() == crdVer && newVer.counter() <= lastCleanupVer.get()))) {
                int cacheId = io.getCacheId(pageAddr, itemId, pageSize);

                assert cacheId != 0;

                MvccLinkAwareSearchRow row = getCleanupRow(link, cacheId);

                return row;
            }
            else
                return null;
        }
    }

    /**
     * @param link Link.
     * @param cacheId CacheId.
     * @return Cleanup search row.
     * @throws IgniteCheckedException If failed.
     */
    private MvccLinkAwareSearchRow getCleanupRow(long link, int cacheId) throws IgniteCheckedException {
        GridCacheContext cctx = ctx.cache().context().cacheContext(cacheId);

        CacheGroupContext grp = ctx.cache().cacheGroup(cacheId);

        MvccDataRow row = new MvccDataRow(link);

        // TODO What to do when persistnace enabled?

        //System.out.println("getCleanupRow cacheId=" + cacheId + "; cctx=" + cctx + "; grp=" + grp);

        if (cctx == null) {
            System.out.println("WTF???????  cctx == null");

            return null; // TODO WTF??
        }


        row.initFromLink(cctx.group(), CacheDataRowAdapter.RowData.KEY_ONLY);

        KeyCacheObject key = row.key();

        int realPartId = cctx.affinity().partition(key);

        long linkPageId = PageIdUtils.pageId(link);

        int linkItem = PageIdUtils.itemId(link);

        long realPageId = PageIdUtils.changePartitionId(linkPageId, realPartId);

        long realLink = PageIdUtils.link(realPageId, linkItem);

        return new MvccLinkAwareSearchRow(cacheId, key, row.mvccCoordinatorVersion(), row.mvccCounter(), realLink);
    }

    /**
     * @param log Logger.
     * @param diagCtx Diagnostic request.
     */
    // TODO: Proper use of diagnostic context.
    public void dumpDebugInfo(IgniteLogger log, @Nullable IgniteDiagnosticPrepareContext diagCtx) {
        boolean first = true;

        for (MvccSnapshotFuture verFur : snapshotFuts.values()) {
            if (first) {
                U.warn(log, "Pending mvcc version futures: ");

                first = false;
            }

            U.warn(log, ">>> " + verFur.toString());
        }

        first = true;

        for (WaitAckFuture waitAckFut : ackFuts.values()) {
            if (first) {
                U.warn(log, "Pending mvcc wait ack futures: ");

                first = false;
            }

            U.warn(log, ">>> " + waitAckFut.toString());
        }
    }

    /**
     * Updates last cleanup version if needed.
     *
     * @param cleanupVer New cleanup version.
     */
    private void updateLastCleanupVersion(long cleanupVer) {
        long lastCleanupVer0;

        do {
            lastCleanupVer0 = lastCleanupVer.get();
        }
        while (cleanupVer > lastCleanupVer0 && !lastCleanupVer.compareAndSet(lastCleanupVer0, cleanupVer));
    }

    /**
     *
     */
    private class MvccSnapshotFuture extends GridFutureAdapter<MvccSnapshot> implements MvccFuture {
        /** */
        private final Long id;

        /** */
        private MvccSnapshotResponseListener lsnr;

        /** */
        public final MvccCoordinator crd;

        /**
         * @param id Future ID.
         * @param crd Mvcc coordinator.
         * @param lsnr Listener.
         */
        MvccSnapshotFuture(Long id, MvccCoordinator crd, @Nullable MvccSnapshotResponseListener lsnr) {
            this.id = id;
            this.crd = crd;
            this.lsnr = lsnr;
        }

        /** {@inheritDoc} */
        @Override public UUID coordinatorNodeId() {
            return crd.nodeId();
        }

        /**
         * @param res Response.
         */
        void onResponse(MvccSnapshotResponse res) {
            assert res.counter() != MVCC_COUNTER_NA;

            updateLastCleanupVersion(res.cleanupVersion());

            if (lsnr != null)
                lsnr.onResponse(crd.nodeId(), res);

            onDone(res);
        }

        /**
         * @param err Error.
         */
        void onError(IgniteCheckedException err) {
            if (lsnr != null)
                lsnr.onError(err);

            onDone(err);
        }

        /**
         * @param nodeId Failed node ID.
         */
        void onNodeLeft(UUID nodeId) {
            if (crd.nodeId().equals(nodeId) && snapshotFuts.remove(id) != null) {
                ClusterTopologyCheckedException err = new ClusterTopologyCheckedException("Failed to request mvcc " +
                    "version, coordinator failed: " + nodeId);

                onError(err);
            }
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return "MvccSnapshotFuture [crd=" + crd.nodeId() + ", id=" + id + ']';
        }
    }

    /**
     *
     */
    private class WaitAckFuture extends GridFutureAdapter<Void> implements MvccFuture {
        /** */
        private final long id;

        /** */
        private final UUID crdId;

        /** */
        final boolean ackTx;

        /**
         * @param id Future ID.
         * @param crdId Coordinator node ID.
         * @param ackTx {@code True} if ack tx commit, {@code false} if waits for previous txs.
         */
        WaitAckFuture(long id, UUID crdId, boolean ackTx) {
            assert crdId != null;

            this.id = id;
            this.crdId = crdId;
            this.ackTx = ackTx;
        }

        /** {@inheritDoc} */
        @Override public UUID coordinatorNodeId() {
            return crdId;
        }

        /**
         *
         */
        void onResponse() {
            onDone();
        }

        /**
         * @param nodeId Failed node ID.
         */
        void onNodeLeft(UUID nodeId) {
            if (crdId.equals(nodeId) && ackFuts.remove(id) != null)
                onDone();
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return "WaitAckFuture [crdId=" + crdId +
                ", id=" + id +
                ", ackTx=" + ackTx + ']';
        }
    }

    /**
     *
     */
    private class CacheCoordinatorNodeFailListener implements GridLocalEventListener {
        /** {@inheritDoc} */
        @Override public void onEvent(Event evt) {
            assert evt instanceof DiscoveryEvent : evt;

            DiscoveryEvent discoEvt = (DiscoveryEvent)evt;

            UUID nodeId = discoEvt.eventNode().id();

            for (MvccSnapshotFuture fut : snapshotFuts.values())
                fut.onNodeLeft(nodeId);

            for (WaitAckFuture fut : ackFuts.values())
                fut.onNodeLeft(nodeId);

            activeQueries.onNodeFailed(nodeId);

            prevCrdQueries.onNodeFailed(nodeId);
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return "CacheCoordinatorDiscoveryListener[]";
        }
    }

    /**
     *
     */
    private class CoordinatorMessageListener implements GridMessageListener {
        /** {@inheritDoc} */
        @Override public void onMessage(UUID nodeId, Object msg, byte plc) {
            MvccMessage msg0 = (MvccMessage)msg;

            if (msg0.waitForCoordinatorInit()) {
                if (crdVer == 0) {
                    try {
                        U.await(crdLatch);
                    }
                    catch (IgniteInterruptedCheckedException e) {
                        U.warn(log, "Failed to wait for coordinator initialization, thread interrupted [" +
                            "msgNode=" + nodeId + ", msg=" + msg + ']');

                        return;
                    }

                    assert crdVer != 0L;
                }
            }

            if (msg instanceof MvccTxSnapshotRequest)
                processCoordinatorTxSnapshotRequest(nodeId, (MvccTxSnapshotRequest)msg);
            else if (msg instanceof MvccAckRequestTx)
                processCoordinatorTxAckRequest(nodeId, (MvccAckRequestTx)msg);
            else if (msg instanceof MvccFutureResponse)
                processCoordinatorAckResponse(nodeId, (MvccFutureResponse)msg);
            else if (msg instanceof MvccAckRequestQuery)
                processCoordinatorQueryAckRequest(nodeId, (MvccAckRequestQuery)msg);
            else if (msg instanceof MvccQuerySnapshotRequest)
                processCoordinatorQuerySnapshotRequest(nodeId, (MvccQuerySnapshotRequest)msg);
            else if (msg instanceof MvccSnapshotResponse)
                processCoordinatorSnapshotResponse(nodeId, (MvccSnapshotResponse)msg);
            else if (msg instanceof MvccWaitTxsRequest)
                processCoordinatorWaitTxsRequest(nodeId, (MvccWaitTxsRequest)msg);
            else if (msg instanceof MvccNewQueryAckRequest)
                processNewCoordinatorQueryAckRequest(nodeId, (MvccNewQueryAckRequest)msg);
            else if (msg instanceof MvccActiveQueriesMessage)
                processCoordinatorActiveQueriesMessage(nodeId, (MvccActiveQueriesMessage)msg);
            else
                U.warn(log, "Unexpected message received [node=" + nodeId + ", msg=" + msg + ']');
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return "CoordinatorMessageListener[]";
        }
    }

    /**
     *
     */
    // TODO: Revisit WaitTxFuture purpose and usages. Looks like we do not need it at all.
    private static class WaitTxFuture extends GridFutureAdapter {
        /** */
        // TODO: Unused?
        private final long txId;

        /**
         * @param txId Transaction ID.
         */
        WaitTxFuture(long txId) {
            this.txId = txId;
        }
    }

    /**
     * Mvcc garbage collection worker. TODO: Indexes, Backups?, Launch options, Persistence, Scan queries cleanup issue
     */
    private class VacuumScheduler extends GridWorker {
        /**
         *
         */
        VacuumScheduler() {
            super(ctx.igniteInstanceName(), "vacuum-scheduler", MvccProcessor.this.log);
        }

        /** {@inheritDoc} */
        @Override protected void body() throws InterruptedException, IgniteInterruptedCheckedException {
            if (isCancelled())
                return;

            try {
                if (log.isInfoEnabled())
                    log.info("Started vacuum cleaner.");

                runVacuum();
            }
            catch (IgniteCheckedException e) {
                log.error("Error occurred when vacuum is running.", e);

                vacuumError = e;
            }
            catch (Throwable e) {
                vacuumError = e;

                throw new IgniteException(e);
            }
            finally {
                synchronized (vacuumEndNotifier) {
                    vacuumEndNotifier.notifyAll();
                }
            }
        }
    }

    /** */
    private static int workerCntr;

    /**
     *
     */
    private class VacuumWorker extends GridWorker {
        /**
         *
         */
        private VacuumWorker() {
            super(ctx.igniteInstanceName(), "vacuum-cleaner-cache-id=" + workerCntr++, MvccProcessor.this.log);
        }

        /** {@inheritDoc} */
        @Override protected void body() throws InterruptedException, IgniteInterruptedCheckedException {
            while (!isCancelled()) {
                T2<Integer, List<MvccLinkAwareSearchRow>> task = cleanupQueue.take();

                Integer cacheId = task.getKey();
                List<MvccLinkAwareSearchRow> cleanupRows = task.getValue();

                GridCacheContext cctx = ctx.cache().context().cacheContext(cacheId);

                System.out.println("cleaned " + cleanupRows.size() + " rows.");

                for (int i = 0; i < cleanupRows.size(); i++) {
                    try {
                        if (isCancelled())
                            return;

                        MvccLinkAwareSearchRow cleanupRow = cleanupRows.get(i);

                        assert cleanupRow != null;

                        GridCacheEntryEx entry = cctx.cache().entryEx(cleanupRow.key());

                        entry.lockEntry();

                        try {
                            //assert row.mvccCounter() == ver.counter() : "row.mvccCounter()=" + row.mvccCounter() + "; ver.counter()=" + ver.counter();

                            int partId = PageIdUtils.partId(PageIdUtils.pageId(cleanupRow.link()));

                            GridDhtLocalPartition part = cctx.topology().localPartition(partId);

                            cctx.offheap().dataStore(part).cleanup(cctx, Collections.singletonList(cleanupRow));
                        }
                        finally {
                            entry.unlockEntry();
                        }
                    }
                    catch (Error e) {
                        vacuumError = e;

                        throw e;
                    }
                    catch (Throwable e) {
                        vacuumError = e;

                        log.error("Problem on vacuum processing.", e);
                    }
                    finally {
                        synchronized (cleanupQueue) {
                            if (cleanupQueue.isEmpty() || Thread.currentThread().isInterrupted())
                                cleanupQueue.notifyAll();
                        }
                    }
                }

                if (log.isDebugEnabled())
                    log.debug("Cleaned " + cleanupRows.size() + " entries.");
            }
        }
    }
}
