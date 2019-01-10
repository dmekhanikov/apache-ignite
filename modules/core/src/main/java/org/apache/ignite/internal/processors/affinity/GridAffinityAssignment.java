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

package org.apache.ignite.internal.processors.affinity;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.apache.ignite.cluster.ClusterNode;
import org.apache.ignite.internal.util.ImmutableBitSet;
import org.apache.ignite.internal.util.typedef.internal.S;

/**
 * Cached affinity calculations.
 */
@SuppressWarnings("ForLoopReplaceableByForEach")
public class GridAffinityAssignment implements AffinityAssignment, Serializable {
    /** */
    private static final long serialVersionUID = 0L;

    /** Topology version. */
    private final AffinityTopologyVersion topVer;

    /** Collection of calculated affinity nodes. */
    private List<List<ClusterNode>> assignment;

    /** Map of primary node partitions. */
    private final Map<UUID, BitSet> primary;

    /** Map of backup node partitions. */
    private final Map<UUID, BitSet> backup;

    /** Assignment node IDs */
    private transient volatile List<HashSet<UUID>> assignmentIds;

    /** Nodes having primary or backup partition assignments. */
    private transient volatile Set<ClusterNode> nodes;

    /** Nodes having primary partitions assignments. */
    private transient volatile Set<ClusterNode> primaryPartsNodes;

    /** */
    private transient List<List<ClusterNode>> idealAssignment;

    /**
     * Constructs cached affinity calculations item.
     *
     * @param topVer Topology version.
     */
    GridAffinityAssignment(AffinityTopologyVersion topVer) {
        this.topVer = topVer;
        primary = Collections.emptyMap();
        backup = Collections.emptyMap();
    }

    /**
     * @param topVer Topology version.
     * @param assignment Assignment.
     * @param idealAssignment Ideal assignment.
     */
    GridAffinityAssignment(AffinityTopologyVersion topVer,
        List<List<ClusterNode>> assignment,
        List<List<ClusterNode>> idealAssignment) {
        assert topVer != null;
        assert assignment != null;
        assert idealAssignment != null;

        this.topVer = topVer;
        this.assignment = Collections.unmodifiableList(assignment);
        this.idealAssignment = idealAssignment.equals(assignment) ? assignment : idealAssignment;

        // Temporary mirrors with modifiable partition's collections.
        Map<UUID, BitSet> tmpPrimary = new HashMap<>();
        Map<UUID, BitSet> tmpBackup = new HashMap<>();
        boolean isFirst;

        for (int partsCnt = assignment.size(), p = 0; p < partsCnt; p++) {
            isFirst = true;

            for (ClusterNode node : assignment.get(p)) {
                UUID id = node.id();

                Map<UUID, BitSet> tmp = isFirst ? tmpPrimary : tmpBackup;

                tmp.computeIfAbsent(id, new Function<UUID, BitSet>() {
                    @Override public BitSet apply(UUID uuid) {
                        return new BitSet(partsCnt);
                    }
                }).set(p);

                isFirst =  false;
            }
        }

        primary = Collections.unmodifiableMap(tmpPrimary);
        backup = Collections.unmodifiableMap(tmpBackup);
    }

    /**
     * @param topVer Topology version.
     * @param aff Assignment to copy from.
     */
    GridAffinityAssignment(AffinityTopologyVersion topVer, GridAffinityAssignment aff) {
        this.topVer = topVer;

        assignment = aff.assignment;
        idealAssignment = aff.idealAssignment;
        primary = aff.primary;
        backup = aff.backup;
    }

    /**
     * @return Affinity assignment computed by affinity function.
     */
    @Override public List<List<ClusterNode>> idealAssignment() {
        return Collections.unmodifiableList(idealAssignment);
    }

    /**
     * @return Affinity assignment.
     */
    @Override public List<List<ClusterNode>> assignment() {
        return Collections.unmodifiableList(assignment);
    }

    /**
     * @return Topology version.
     */
    @Override public AffinityTopologyVersion topologyVersion() {
        return topVer;
    }

    /**
     * Get affinity nodes for partition.
     *
     * @param part Partition.
     * @return Affinity nodes.
     */
    @Override public List<ClusterNode> get(int part) {
        assert part >= 0 && part < assignment.size() : "Affinity partition is out of range" +
            " [part=" + part + ", partitions=" + assignment.size() + ']';

        return assignment.get(part);
    }

    /**
     * Get affinity node IDs for partition.
     *
     * @param part Partition.
     * @return Affinity nodes IDs.
     */
    @Override public HashSet<UUID> getIds(int part) {
        assert part >= 0 && part < assignment.size() : "Affinity partition is out of range" +
            " [part=" + part + ", partitions=" + assignment.size() + ']';

        List<HashSet<UUID>> assignmentIds0 = assignmentIds;

        if (assignmentIds0 == null) {
            assignmentIds0 = new ArrayList<>();

            for (List<ClusterNode> assignmentPart : assignment) {
                HashSet<UUID> partIds = new HashSet<>();

                for (ClusterNode node : assignmentPart)
                    partIds.add(node.id());

                assignmentIds0.add(partIds);
            }

            assignmentIds = assignmentIds0;
        }

        return assignmentIds0.get(part);
    }

    /** {@inheritDoc} */
    @Override public Set<ClusterNode> nodes() {
        Set<ClusterNode> res = nodes;

        if (res == null) {
            res = new HashSet<>(assignment.size());

            for (int p = 0; p < assignment.size(); p++) {
                List<ClusterNode> nodes = assignment.get(p);

                if (!nodes.isEmpty())
                    res.addAll(nodes);
            }

            nodes = res;
        }

        return res;
    }

    /** {@inheritDoc} */
    @Override public Set<ClusterNode> primaryPartitionNodes() {
        Set<ClusterNode> res = primaryPartsNodes;

        if (res == null) {
            res = new HashSet<>(assignment.size());

            for (int p = 0; p < assignment.size(); p++) {
                List<ClusterNode> nodes = assignment.get(p);

                if (!nodes.isEmpty())
                    res.add(nodes.get(0));
            }

            primaryPartsNodes = res;
        }

        return res;
    }

    /**
     * Get primary partitions for specified node ID.
     *
     * @param nodeId Node ID to get primary partitions for.
     * @return Primary partitions for specified node ID.
     */
    @Override public Set<Integer> primaryPartitions(UUID nodeId) {
        BitSet set = primary.get(nodeId);

        return set == null ? Collections.emptySet() : new ImmutableBitSet(set);
    }

    /**
     * Get backup partitions for specified node ID.
     *
     * @param nodeId Node ID to get backup partitions for.
     * @return Backup partitions for specified node ID.
     */
    @Override public Set<Integer> backupPartitions(UUID nodeId) {
        BitSet set = backup.get(nodeId);

        return set == null ? Collections.emptySet() : new ImmutableBitSet(set);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return topVer.hashCode();
    }

    /** {@inheritDoc} */
    @SuppressWarnings("SimplifiableIfStatement")
    @Override public boolean equals(Object o) {
        if (o == this)
            return true;

        if (!(o instanceof AffinityAssignment))
            return false;

        return topVer.equals(((AffinityAssignment)o).topologyVersion());
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridAffinityAssignment.class, this, super.toString());
    }
}
