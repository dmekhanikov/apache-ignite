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
 *
 */

package org.apache.ignite.internal.stat;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.ignite.mxbean.IoStatMetricsMXBean;

import static org.apache.ignite.internal.stat.GridIoStatManager.HASH_PK_INDEX_NAME;

/**
 * JMX bean to expose local node IO statistics.
 */
public class IoStatMetricsLocalMXBeanImpl implements IoStatMetricsMXBean {
    /** IO statistic manager. */
    private GridIoStatManager statMgr;

    /**
     * @param statMgr IO statistic manager.
     */
    public IoStatMetricsLocalMXBeanImpl(GridIoStatManager statMgr) {
        this.statMgr = statMgr;
    }

    /** {@inheritDoc} */
    @Override public LocalDateTime getStartGatheringStatistics() {
        return statMgr.statsSince();
    }

    /** {@inheritDoc} */
    @Override public void resetStatistics() {
        statMgr.resetStats();
    }

    /** {@inheritDoc} */
    @Override public String getCacheStatisticsFormatted(String cacheGrpName) {
        return formattedStats(StatType.CACHE_GROUP, cacheGrpName, null);
    }

    /** {@inheritDoc} */
    @Override public Long getCachePhysicalReadsStatistics(String cacheGrpName) {
        return statMgr.physicalReads(StatType.CACHE_GROUP, cacheGrpName);
    }

    /** {@inheritDoc} */
    @Override public Long getCacheLogicalReadsStatistics(String cacheGrpName) {
        return statMgr.logicalReads(StatType.CACHE_GROUP, cacheGrpName);
    }

    /** {@inheritDoc} */
    @Override public String getIndexStatisticsFormatted(String cacheGrpName, String idxName) {
        return formattedStats(getIndexStatType(idxName), cacheGrpName, idxName);
    }

    /**
     * @param idxName Name of index
     * @return Type of index statistics.
     */
    private StatType getIndexStatType(String idxName) {
        return idxName.equals(HASH_PK_INDEX_NAME) ? StatType.HASH_INDEX : StatType.SORTED_INDEX;
    }

    /**
     * Gets string presentation of IO statistics for given parameters.
     *
     * @param statType Type of statistics.
     * @param name Name of statistics
     * @param subName SubName of statistics.
     * @return String presentation of IO statistics for given parameters.
     */
    private String formattedStats(StatType statType, String name, String subName) {
        Map<String, Long> logicalReads = statMgr.logicalReadsByTypes(statType, name, subName);

        Map<String, Long> physicalReads = statMgr.physicalReadsByTypes(statType, name, subName);

        String stats = Stream.concat(logicalReads.entrySet().stream(), physicalReads.entrySet().stream())
            .map(e -> e.getKey() + "=" + e.getValue())
            .collect(Collectors.joining(", ", "[", "]"));

        String statinfo = statType.name() + " " + (subName != null ? name + "." + subName : name);

        return statinfo + " " + stats;
    }

    /** {@inheritDoc} */
    @Override public Long getIndexPhysicalReadsStatistics(String cacheGrpName, String idxName) {
        return statMgr.physicalReads(getIndexStatType(idxName), cacheGrpName, idxName);
    }

    /** {@inheritDoc} */
    @Override public Long getIndexLogicalReadsStatistics(String cacheGrpName, String idxName) {
        return statMgr.logicalReads(getIndexStatType(idxName), cacheGrpName, idxName);
    }

    /** {@inheritDoc} */
    @Override public Long getIndexLeafLogicalReadsStatistics(String cacheGrpName, String idxName) {
        Map<String, Long> logicalReads = statMgr.logicalReadsByTypes(getIndexStatType(idxName), cacheGrpName, idxName);

        return logicalReads.get(StatisticsHolderIndex.LOGICAL_READS_LEAF);
    }

    /** {@inheritDoc} */
    @Override public Long getIndexLeafPhysicalReadsStatistics(String cacheGrpName, String idxName) {
        Map<String, Long> logicalReads = statMgr.logicalReadsByTypes(getIndexStatType(idxName), cacheGrpName, idxName);

        return logicalReads.get(StatisticsHolderIndex.PHYSICAL_READS_LEAF);
    }

    /** {@inheritDoc} */
    @Override public Long getIndexInnerLogicalReadsStatistics(String cacheGrpName, String idxName) {
        Map<String, Long> logicalReads = statMgr.logicalReadsByTypes(getIndexStatType(idxName), cacheGrpName, idxName);

        return logicalReads.get(StatisticsHolderIndex.LOGICAL_READS_INNER);
    }

    /** {@inheritDoc} */
    @Override public Long getIndexInnerPhysicalReadsStatistics(String cacheGrpName, String idxName) {
        Map<String, Long> logicalReads = statMgr.logicalReadsByTypes(getIndexStatType(idxName), cacheGrpName, idxName);

        return logicalReads.get(StatisticsHolderIndex.PHYSICAL_READS_INNER);
    }

    /** {@inheritDoc} */
    @Override public Set<String> getStatHashIndexesNames(String cacheGrpName) {
        return statMgr.deriveStatSubNames(StatType.HASH_INDEX, cacheGrpName);
    }

    /** {@inheritDoc} */
    @Override public Set<String> getStatSortedIndexesNames(String cacheGrpName) {
        return statMgr.deriveStatSubNames(StatType.SORTED_INDEX, cacheGrpName);
    }

    /** {@inheritDoc} */
    @Override public Set<String> getStatCachesNames() {
        return statMgr.deriveStatNames(StatType.CACHE_GROUP);
    }
}
