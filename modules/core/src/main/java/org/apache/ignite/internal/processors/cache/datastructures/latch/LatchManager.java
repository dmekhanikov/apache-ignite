package org.apache.ignite.internal.processors.cache.datastructures.latch;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.IgniteLogger;
import org.apache.ignite.cluster.ClusterNode;
import org.apache.ignite.internal.GridKernalContext;
import org.apache.ignite.internal.GridTopic;
import org.apache.ignite.internal.managers.communication.GridIoManager;
import org.apache.ignite.internal.managers.communication.GridIoPolicy;
import org.apache.ignite.internal.managers.discovery.GridDiscoveryManager;
import org.apache.ignite.internal.processors.affinity.AffinityTopologyVersion;
import org.apache.ignite.internal.util.GridConcurrentHashSet;
import org.apache.ignite.internal.util.future.GridFutureAdapter;
import org.apache.ignite.internal.util.typedef.T2;

import static org.apache.ignite.events.EventType.EVT_NODE_FAILED;
import static org.apache.ignite.events.EventType.EVT_NODE_LEFT;

public class LatchManager {

    private final IgniteLogger log;

    private final GridKernalContext ctx;

    private final GridDiscoveryManager discovery;

    private final GridIoManager io;

    private volatile ClusterNode coordinator;

    private final ConcurrentMap<T2<String, AffinityTopologyVersion>, Set<UUID>> pendingAcks = new ConcurrentHashMap<>();

    private final ConcurrentMap<T2<String, AffinityTopologyVersion>, ServerLatch> serverLatches = new ConcurrentHashMap<>();

    private final ConcurrentMap<T2<String, AffinityTopologyVersion>, ClientLatch> clientLatches = new ConcurrentHashMap<>();

    private final ReentrantLock lock = new ReentrantLock();

    public LatchManager(GridKernalContext ctx) {
        this.ctx = ctx;
        this.log = ctx.log(getClass());
        this.discovery = ctx.discovery();
        this.io = ctx.io();

        if (!ctx.clientNode()) {
            ctx.io().addMessageListener(GridTopic.TOPIC_LATCH, (nodeId, msg, plc) -> {
                if (msg instanceof LatchAckMessage) {
                    processAck(nodeId, (LatchAckMessage) msg);
                }
            });

            ctx.event().addDiscoveryEventListener((e, cache) -> {
                assert e != null;
                assert e.type() == EVT_NODE_LEFT || e.type() == EVT_NODE_FAILED : this;

                processNodeLeft(e.eventNode());
            }, EVT_NODE_LEFT, EVT_NODE_FAILED);
        }
    }

    public void release(String id, AffinityTopologyVersion topVer, ClusterNode node) {
        lock.lock();

        final T2<String, AffinityTopologyVersion> latchId = new T2<>(id, topVer);

        try {
            assert !serverLatches.containsKey(latchId);

            pendingAcks.remove(latchId);

            // Send final ack.
            io.sendToGridTopic(node, GridTopic.TOPIC_LATCH, new LatchAckMessage(id, topVer, true), GridIoPolicy.SYSTEM_POOL);

            if (log.isDebugEnabled())
                log.debug("Release final ack is ackSent [latch=" + latchId + ", to=" + node.id() + "]");
        }
        catch (IgniteCheckedException e) {
            if (log.isDebugEnabled())
                log.debug("Unable to send release final ack [latch=" + latchId + ", to=" + node.id() + "]: " + e.getMessage());
        }
        finally {
            lock.unlock();
        }
    }

    private Latch createServerLatch(String id, AffinityTopologyVersion topVer, Collection<ClusterNode> participants) {
        final T2<String, AffinityTopologyVersion> latchId = new T2<>(id, topVer);

        if (serverLatches.containsKey(latchId))
            return serverLatches.get(latchId);

        ServerLatch latch = new ServerLatch(id, topVer, participants);

        serverLatches.put(latchId, latch);

        if (log.isDebugEnabled())
            log.debug("Server latch is created [latch=" + latchId + ", participantsSize=" + participants.size() + "]");

        if (pendingAcks.containsKey(latchId)) {
            Set<UUID> acks = pendingAcks.get(latchId);

            for (UUID node : acks)
                if (latch.hasParticipant(node) && !latch.hasAck(node))
                    latch.ack(node);

            pendingAcks.remove(latchId);
        }

        if (latch.isCompleted()) {
            serverLatches.remove(latchId);
        }

        return latch;
    }

    private Latch createClientLatch(String id, AffinityTopologyVersion topVer, ClusterNode coordinator, Collection<ClusterNode> participants) {
        final T2<String, AffinityTopologyVersion> latchId = new T2<>(id, topVer);

        if (clientLatches.containsKey(latchId))
            return clientLatches.get(latchId);

        ClientLatch latch = new ClientLatch(id, topVer, coordinator, participants);

        if (log.isDebugEnabled())
            log.debug("Client latch is created [latch=" + latchId
                    + ", crd=" + coordinator
                    + ", participantsSize=" + participants.size() + "]");

        // There is final ack for created latch.
        if (pendingAcks.containsKey(latchId)) {
            latch.complete();
            pendingAcks.remove(latchId);
        }
        else
            clientLatches.put(latchId, latch);

        return latch;
    }

    public Latch getOrCreate(String id, AffinityTopologyVersion topVer) {
        lock.lock();

        try {
            ClusterNode coordinator = discovery.discoCache(topVer).oldestAliveServerNode();

            if (this.coordinator == null)
                this.coordinator = coordinator;

            if (coordinator == null) {
                ClientLatch latch = new ClientLatch(id, AffinityTopologyVersion.NONE, null, Collections.emptyList());
                latch.complete();

                return latch;
            }

            Collection<ClusterNode> participants = discovery.discoCache(topVer).aliveServerNodes();

            if (coordinator.isLocal())
                return createServerLatch(id, topVer, participants);
            else
                return createClientLatch(id, topVer, coordinator, participants);
        }
        finally {
            lock.unlock();
        }
    }

    private void processAck(UUID from, LatchAckMessage message) {
        lock.lock();

        try {
            ClusterNode coordinator = discovery.oldestAliveServerNode(AffinityTopologyVersion.NONE);

            if (coordinator == null)
                return;

            T2<String, AffinityTopologyVersion> latchId = new T2<>(message.latchId(), message.topVer());

            if (message.isFinal()) {
                if (log.isDebugEnabled())
                    log.debug("Process final ack [latch=" + latchId + ", from=" + from + "]");

                if (clientLatches.containsKey(latchId)) {
                    ClientLatch latch = clientLatches.remove(latchId);
                    latch.complete();
                }
                else if (!coordinator.isLocal()) {
                    pendingAcks.computeIfAbsent(latchId, (id) -> new GridConcurrentHashSet<>());
                    pendingAcks.get(latchId).add(from);
                }
            } else {
                if (log.isDebugEnabled())
                    log.debug("Process ack [latch=" + latchId + ", from=" + from + "]");

                if (serverLatches.containsKey(latchId)) {
                    ServerLatch latch = serverLatches.get(latchId);

                    if (latch.hasParticipant(from) && !latch.hasAck(from)) {
                        latch.ack(from);

                        if (latch.isCompleted())
                            serverLatches.remove(latchId);
                    }
                }
                else {
                    pendingAcks.computeIfAbsent(latchId, (id) -> new GridConcurrentHashSet<>());
                    pendingAcks.get(latchId).add(from);
                }
            }
        }
        finally {
            lock.unlock();
        }
    }

    private void becomeNewCoordinator() {
        if (log.isInfoEnabled())
            log.info("Become new coordinator " + coordinator.id());

        List<T2<String, AffinityTopologyVersion>> latchesToRestore = new ArrayList<>();
        // Restore latches from pending acks and own proxy latches.
        latchesToRestore.addAll(pendingAcks.keySet());
        latchesToRestore.addAll(clientLatches.keySet());

        for (T2<String, AffinityTopologyVersion> latchId : latchesToRestore) {
            String id = latchId.get1();

            AffinityTopologyVersion topVer = latchId.get2();

            Collection<ClusterNode> participants = discovery.discoCache(topVer).aliveServerNodes();

            if (!participants.isEmpty()) {
                createServerLatch(id, topVer, participants);
            }
        }
    }

    private void processNodeLeft(ClusterNode left) {
        lock.lock();

        try {
            if (log.isDebugEnabled())
                log.debug("Process node left " + left.id());

            ClusterNode coordinator = discovery.oldestAliveServerNode(AffinityTopologyVersion.NONE);

            assert coordinator != null;

            // Clear pending acks.
            for (Map.Entry<T2<String, AffinityTopologyVersion>, Set<UUID>> ackEntry : pendingAcks.entrySet()) {
                if (ackEntry.getValue().contains(left.id())) {
                    pendingAcks.get(ackEntry.getKey()).remove(left.id());
                }
            }

            // Change coordinators for proxy latches.
            for (Map.Entry<T2<String, AffinityTopologyVersion>, ClientLatch> latchEntry : clientLatches.entrySet()) {
                ClientLatch latch = latchEntry.getValue();
                if (latch.hasCoordinator(left.id())) {
                    // Change coordinator for latch and re-send ack if necessary.
                    if (latch.hasParticipant(coordinator.id())) {
                        latch.newCoordinator(coordinator);
                    }
                    else {
                        latch.complete(new IgniteCheckedException("Coordinator is left from topology."));
                    }
                }
            }

            // Add acknowledgements from left node.
            for (Map.Entry<T2<String, AffinityTopologyVersion>, ServerLatch> latchEntry : serverLatches.entrySet()) {
                ServerLatch latch = latchEntry.getValue();

                if (latch.hasParticipant(left.id()) && !latch.hasAck(left.id())) {
                    if (log.isDebugEnabled())
                        log.debug("Process node left [latch=" + latchEntry.getKey() + ", left=" + left.id() + "]");

                    latch.ack(left.id());

                    if (latch.isCompleted())
                        serverLatches.remove(latchEntry.getKey());
                }
            }

            // Coordinator is changed.
            if (coordinator.isLocal() && this.coordinator.id() != coordinator.id()) {
                this.coordinator = coordinator;

                becomeNewCoordinator();
            }
        }
        finally {
            lock.unlock();
        }
    }

    class ServerLatch extends CompletableLatch {
        private final AtomicInteger permits;

        private final Set<UUID> acks = new GridConcurrentHashSet<>();

        ServerLatch(String id, AffinityTopologyVersion topVer, Collection<ClusterNode> participants) {
            super(id, topVer, participants);
            this.permits = new AtomicInteger(participants.size());

            this.complete.listen(f -> {
                // Send final acks.
                for (ClusterNode node : participants) {
                    try {
                        if (discovery.alive(node)) {
                            io.sendToGridTopic(node, GridTopic.TOPIC_LATCH, new LatchAckMessage(id, topVer, true), GridIoPolicy.SYSTEM_POOL);

                            if (log.isDebugEnabled())
                                log.debug("Final ack is ackSent [latch=" + latchId() + ", to=" + node.id() + "]");
                        }
                    } catch (IgniteCheckedException e) {
                        if (log.isDebugEnabled())
                            log.debug("Unable to send final ack [latch=" + latchId() + ", to=" + node.id() + "]");
                    }
                }
            });
        }

        boolean hasAck(UUID from) {
            return acks.contains(from);
        }

        void ack(UUID from) {
            if (log.isDebugEnabled())
                log.debug("Ack is accepted [latch=" + latchId() + ", from=" + from + "]");

            countDown0(from);
        }

        private void countDown0(UUID node) {
            if (isCompleted() || acks.contains(node))
                return;

            acks.add(node);

            int remaining = permits.decrementAndGet();

            if (log.isDebugEnabled())
                log.debug("Count down + [latch=" + latchId() + ", remaining=" + remaining + "]");

            if (remaining == 0)
                complete();
        }

        @Override public void countDown() {
            countDown0(ctx.localNodeId());
        }
    }

    /**
     * Latch is created on non-coordinator node.
     * Latch completes when final ack from coordinator is received.
     */
    class ClientLatch extends CompletableLatch {
        /** Latch coordinator node. Can be changed if coordinator is left from topology. */
        private volatile ClusterNode coordinator;

        /** Flag indicates that ack is sent to coordinator. */
        private boolean ackSent = false;

        /**
         * Constructor.
         *
         * @param id Latch id.
         * @param topVer Latch topology version.
         * @param coordinator Coordinator node.
         * @param participants Participant nodes.
         */
        ClientLatch(String id, AffinityTopologyVersion topVer, ClusterNode coordinator, Collection<ClusterNode> participants) {
            super(id, topVer, participants);

            this.coordinator = coordinator;
        }

        /**
         * Checks if latch coordinator is given {@code node}.
         *
         * @param node Node.
         * @return {@code true} if latch coordinator is given node.
         */
        boolean hasCoordinator(UUID node) {
            return coordinator.id().equals(node);
        }

        /**
         * Changes coordinator of latch and resends ack to new coordinator if needed.
         *
         * @param coordinator New coordinator.
         */
        void newCoordinator(ClusterNode coordinator) {
            this.coordinator = coordinator;

            if (log.isDebugEnabled())
                log.debug("Coordinator is changed [latch=" + latchId() + ", crd=" + coordinator.id() + "]");

            synchronized (this) {
                // Resend ack to new coordinator.
                if (ackSent)
                    sendAck();
            }
        }

        /**
         * Sends ack to coordinator node.
         * There is ack deduplication on coordinator. So it's fine to send same ack twice.
         */
        private void sendAck() {
            try {
                ackSent = true;

                io.sendToGridTopic(coordinator, GridTopic.TOPIC_LATCH, new LatchAckMessage(id, topVer, false), GridIoPolicy.SYSTEM_POOL);

                if (log.isDebugEnabled())
                    log.debug("Ack is ackSent + [latch=" + latchId() + ", to=" + coordinator.id() + "]");
            } catch (IgniteCheckedException e) {
                // Coordinator is unreachable. On coodinator node left discovery event ack will be resent.
                if (log.isDebugEnabled())
                    log.debug("Unable to send ack [latch=" + latchId() + ", to=" + coordinator.id() + "]: " + e.getMessage());
            }
        }

        @Override public void countDown() {
            if (isCompleted())
                return;

            // Synchronize in case of changed coordinator.
            synchronized (this) {
                sendAck();
            }
        }
    }

    /**
     * Base latch functionality with implemented complete / await logic.
     */
    private abstract class CompletableLatch implements Latch {
        /** Latch id. */
        protected final String id;

        /** Latch topology version. */
        protected final AffinityTopologyVersion topVer;

        /** Latch node participants. Only participant nodes are able to change state of latch. */
        protected final Set<UUID> participants;

        /** Future indicates that latch is completed. */
        protected final GridFutureAdapter<?> complete = new GridFutureAdapter<>();

        /**
         * Constructor.
         *
         * @param id Latch id.
         * @param topVer Latch topology version.
         * @param participants Participant nodes.
         */
        CompletableLatch(String id, AffinityTopologyVersion topVer, Collection<ClusterNode> participants) {
            this.id = id;
            this.topVer = topVer;
            this.participants = participants.stream().map(ClusterNode::id).collect(Collectors.toSet());
        }

        @Override public void await() throws IgniteCheckedException {
            complete.get();
        }

        @Override public void await(long timeout, TimeUnit timeUnit) throws IgniteCheckedException {
            complete.get(timeout, timeUnit);
        }

        /**
         * Checks if latch participants contain given {@code node}.
         *
         * @param node Node.
         * @return {@code true} if latch participants contain given node.
         */
        boolean hasParticipant(UUID node) {
            return participants.contains(node);
        }

        /**
         * @return {@code true} if latch is completed.
         */
        boolean isCompleted() {
            return complete.isDone();
        }

        /**
         * Completes current latch.
         */
        void complete() {
            complete.onDone();
        }

        /**
         * Completes current latch with given {@code error}.
         *
         * @param error Error.
         */
        void complete(Throwable error) {
            complete.onDone(error);
        }

        /**
         * @return Full latch id.
         */
        String latchId() {
            return id + "-" + topVer;
        }
    }
}
