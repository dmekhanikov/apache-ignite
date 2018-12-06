package org.apache.ignite.internal.processors.cache.mvcc;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.internal.IgniteInternalFuture;
import org.apache.ignite.internal.processors.cache.GridCacheSharedContext;
import org.apache.ignite.internal.processors.cache.distributed.near.GridNearTxLocal;
import org.apache.ignite.internal.processors.cache.transactions.IgniteInternalTx;
import org.apache.ignite.internal.processors.cache.version.GridCacheVersion;

import static org.apache.ignite.internal.GridTopic.TOPIC_CACHE_COORDINATOR;
import static org.apache.ignite.internal.managers.communication.GridIoPolicy.SYSTEM_POOL;

// t0d0 meaningful name
public class DdCollaborator {
    private final GridCacheSharedContext<?, ?> cctx;

    public DdCollaborator(GridCacheSharedContext<?, ?> cctx) {
        this.cctx = cctx;
    }

    public void startComputation(MvccVersion waiterVersion, MvccVersion blockerVersion) {
        Optional<IgniteInternalTx> waitingTx = cctx.tm().activeTransactions().stream()
            .filter(tx -> belongToSameTx(waiterVersion, tx.mvccSnapshot()))
            .findAny();
        Optional<IgniteInternalTx> blockerTx = cctx.tm().activeTransactions().stream()
            .filter(tx -> belongToSameTx(blockerVersion, tx.mvccSnapshot()))
            .findAny();
        if (waitingTx.isPresent() && blockerTx.isPresent()) {
            sendProbe(
                waitingTx.get().nearXidVersion(),
                waitingTx.get().nearXidVersion(),
                blockerTx.get().nearXidVersion(),
                blockerTx.get().eventNodeId()
            );
        }
    }

    public void handleDeadlockProbe(DeadlockProbe probe) {
        // a probe is simply discarded if next wait-for edge is not found
        cctx.tm().activeTransactions().stream()
            .filter(tx -> tx.nearXidVersion().equals(probe.blockerVersion()) && tx.near() && tx.local())
            .map(GridNearTxLocal.class::cast)
            .findAny()
            .ifPresent(tx -> {
                if (tx.nearXidVersion().equals(probe.initiatorVersion())) {
                    // a deadlock found
                    tx.rollbackAsync();
                }
                else {
                    // probe each blocker
                    // t0d0 pending responses from MULTIPLE nodes, MULTIPLE blocking transactions from each node
                    // consider grouping (only if it will lead to correct results!)
                    // t0d0 check if holding some lock already
                    // t0d0 first find all peers then send messages
                    collectBlockers(tx).forEach(fut -> {
                        fut.listen(fut0 -> {
                            try {
                                NearTxLocator blockerTx = fut.get();

                                sendProbe(
                                    probe.initiatorVersion(),
                                    tx.nearXidVersion(),
                                    blockerTx.xidVersion(),
                                    blockerTx.nodeId());
                            }
                            catch (IgniteCheckedException e) {
                                e.printStackTrace();
                            }
                        });
                    });
                }
            });
    }

    private Collection<IgniteInternalFuture<NearTxLocator>> collectBlockers(GridNearTxLocal tx) {
        return tx.getPendingResponseNodes().stream()
            .map(nodeId -> cctx.coordinators().checkWaiting(nodeId, tx.mvccSnapshot()))
            .collect(Collectors.toList());
    }

    private void sendProbe(
        GridCacheVersion initiatorVer, GridCacheVersion waiterVer, GridCacheVersion blockerVer, UUID blockerNearNodeId) {
        // t0d0 review if message order is important here
        // t0d0 PROPER TOPIC
        DeadlockProbe probe = new DeadlockProbe(initiatorVer, waiterVer, blockerVer);
        try {
            cctx.gridIO().sendToGridTopic(
                blockerNearNodeId, TOPIC_CACHE_COORDINATOR, probe, SYSTEM_POOL);
        }
        catch (IgniteCheckedException e) {
            // t0d0 handle send errors
            e.printStackTrace();
        }
    }

    public static boolean belongToSameTx(MvccVersion v1, MvccVersion v2) {
        return v1.coordinatorVersion() == v2.coordinatorVersion() && v1.counter() == v2.counter();
    }
}
