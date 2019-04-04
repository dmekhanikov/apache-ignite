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

package org.apache.ignite.ml.selection.scoring.evaluator;

import java.io.Serializable;
import java.util.Map;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.lang.IgniteBiPredicate;
import org.apache.ignite.ml.IgniteModel;
import org.apache.ignite.ml.dataset.feature.extractor.Vectorizer;
import org.apache.ignite.ml.math.primitives.vector.Vector;
import org.apache.ignite.ml.selection.scoring.cursor.CacheBasedLabelPairCursor;
import org.apache.ignite.ml.selection.scoring.cursor.LabelPairCursor;
import org.apache.ignite.ml.selection.scoring.cursor.LocalLabelPairCursor;
import org.apache.ignite.ml.selection.scoring.metric.Metric;
import org.apache.ignite.ml.selection.scoring.metric.classification.BinaryClassificationMetricValues;
import org.apache.ignite.ml.selection.scoring.metric.classification.BinaryClassificationMetrics;

/**
 * Evaluator that computes metrics from predictions and ground truth values.
 */
public class Evaluator {
    /**
     * Computes the given metric on the given cache.
     *
     * @param dataCache The given cache.
     * @param mdl       The model.
     * @param metric    The binary classification metric.
     * @param <K>       The type of cache entry key.
     * @param <V>       The type of cache entry value.
     * @return Computed metric.
     */
    public static <L, K, V, C extends Serializable> double evaluate(IgniteCache<K, V> dataCache,
                                                                    IgniteModel<Vector, L> mdl,
                                                                    Vectorizer<K, V, C, L> vectorizer,
                                                                    Metric<L> metric) {
        return calculateMetric(dataCache, null, mdl, vectorizer, metric);
    }

    /**
     * Computes the given metric on the given cache.
     *
     * @param dataCache The given local data.
     * @param mdl       The model.
     * @param metric    The binary classification metric.
     * @param <K>       The type of cache entry key.
     * @param <V>       The type of cache entry value.
     * @return Computed metric.
     */
    public static <L, K, V, C extends Serializable> double evaluate(Map<K, V> dataCache,
                                                                    IgniteModel<Vector, L> mdl,
                                                                    Vectorizer<K, V, C, L> vectorizer,
                                                                    Metric<L> metric) {
        return calculateMetric(dataCache, null, mdl, vectorizer, metric);
    }

    /**
     * Computes the given metric on the given cache.
     *
     * @param dataCache The given cache.
     * @param filter    The given filter.
     * @param mdl       The model.
     * @param metric    The binary classification metric.
     * @param <L>       The type of label.
     * @param <K>       The type of cache entry key.
     * @param <V>       The type of cache entry value.
     * @return Computed metric.
     */
    public static <L, K, V, C extends Serializable> double evaluate(IgniteCache<K, V> dataCache, IgniteBiPredicate<K, V> filter,
                                                                    IgniteModel<Vector, L> mdl,
                                                                    Vectorizer<K, V, C, L> vectorizer,
                                                                    Metric<L> metric) {
        return calculateMetric(dataCache, filter, mdl, vectorizer, metric);
    }

    /**
     * Computes the given metric on the given cache.
     *
     * @param dataCache The given cache.
     * @param filter    The given filter.
     * @param mdl       The model.
     * @param metric    The binary classification metric.
     * @param <L>       The type of label.
     * @param <K>       The type of cache entry key.
     * @param <V>       The type of cache entry value.
     * @return Computed metric.
     */
    public static <L, K, V, C extends Serializable> double evaluate(Map<K, V> dataCache, IgniteBiPredicate<K, V> filter,
                                                                    IgniteModel<Vector, L> mdl,
                                                                    Vectorizer<K, V, C, L> vectorizer,
                                                                    Metric<L> metric) {
        return calculateMetric(dataCache, filter, mdl, vectorizer, metric);
    }

    /**
     * Computes the given metrics on the given cache.
     *
     * @param dataCache The given cache.
     * @param mdl       The model.
     * @param <K>       The type of cache entry key.
     * @param <V>       The type of cache entry value.
     * @return Computed metric.
     */
    public static <K, V> BinaryClassificationMetricValues evaluate(IgniteCache<K, V> dataCache,
                                                                   IgniteModel<Vector, Double> mdl, Vectorizer<K, V, Integer, Double> vectorizer) {
        return calcMetricValues(dataCache, null, mdl, vectorizer);
    }

    /**
     * Computes the given metrics on the given cache.
     *
     * @param dataCache The given cache.
     * @param mdl       The model.
     * @param <K>       The type of cache entry key.
     * @param <V>       The type of cache entry value.
     * @return Computed metric.
     */
    public static <K, V> BinaryClassificationMetricValues evaluate(Map<K, V> dataCache,
                                                                   IgniteModel<Vector, Double> mdl,
                                                                   Vectorizer<K, V, Integer, Double> vectorizer) {
        return calcMetricValues(dataCache, null, mdl, vectorizer);
    }

    /**
     * Computes the given metrics on the given cache.
     *
     * @param dataCache The given cache.
     * @param filter    The given filter.
     * @param mdl       The model.
     * @param <K>       The type of cache entry key.
     * @param <V>       The type of cache entry value.
     * @return Computed metric.
     */
    public static <K, V> BinaryClassificationMetricValues evaluate(IgniteCache<K, V> dataCache,
                                                                   IgniteBiPredicate<K, V> filter,
                                                                   IgniteModel<Vector, Double> mdl,
                                                                   Vectorizer<K, V, Integer, Double> vectorizer) {
        return calcMetricValues(dataCache, filter, mdl, vectorizer);
    }

    /**
     * Computes the given metrics on the given cache.
     *
     * @param dataCache The given cache.
     * @param filter    The given filter.
     * @param mdl       The model.
     * @param <K>       The type of cache entry key.
     * @param <V>       The type of cache entry value.
     * @return Computed metric.
     */
    public static <K, V> BinaryClassificationMetricValues evaluate(Map<K, V> dataCache, IgniteBiPredicate<K, V> filter,
                                                                   IgniteModel<Vector, Double> mdl,
                                                                   Vectorizer<K, V, Integer, Double> vectorizer) {
        return calcMetricValues(dataCache, filter, mdl, vectorizer);
    }

    /**
     * Computes the given metrics on the given cache.
     *
     * @param dataCache The given cache.
     * @param filter    The given filter.
     * @param mdl       The model.
     * @param <K>       The type of cache entry key.
     * @param <V>       The type of cache entry value.
     * @return Computed metric.
     */
    private static <K, V> BinaryClassificationMetricValues calcMetricValues(IgniteCache<K, V> dataCache,
                                                                            IgniteBiPredicate<K, V> filter,
                                                                            IgniteModel<Vector, Double> mdl,
                                                                            Vectorizer<K, V, Integer, Double> vectorizer) {
        BinaryClassificationMetricValues metricValues;
        BinaryClassificationMetrics binaryMetrics = new BinaryClassificationMetrics();

        try (LabelPairCursor<Double> cursor = new CacheBasedLabelPairCursor<>(
            dataCache,
            vectorizer,
            mdl
        )) {
            metricValues = binaryMetrics.scoreAll(cursor.iterator());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return metricValues;
    }

    /**
     * Computes the given metrics on the given cache.
     *
     * @param dataCache The given cache.
     * @param filter    The given filter.
     * @param mdl       The model.
     * @param <K>       The type of cache entry key.
     * @param <V>       The type of cache entry value.
     * @return Computed metric.
     */
    private static <K, V> BinaryClassificationMetricValues calcMetricValues(Map<K, V> dataCache,
                                                                            IgniteBiPredicate<K, V> filter,
                                                                            IgniteModel<Vector, Double> mdl,
                                                                            Vectorizer<K, V, Integer, Double> vectorizer) {
        BinaryClassificationMetricValues metricValues;
        BinaryClassificationMetrics binaryMetrics = new BinaryClassificationMetrics();

        try (LabelPairCursor<Double> cursor = new LocalLabelPairCursor<>(
            dataCache,
            filter,
            vectorizer,
            mdl
        )) {
            metricValues = binaryMetrics.scoreAll(cursor.iterator());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return metricValues;
    }

    /**
     * Computes the given metric on the given cache.
     *
     * @param dataCache The given cache.
     * @param filter    The given filter.
     * @param metric    The binary classification metric.
     * @param <L>       The type of label.
     * @param <K>       The type of cache entry key.
     * @param <V>       The type of cache entry value.
     * @return Computed metric.
     */
    private static <L, K, V, C extends Serializable> double calculateMetric(IgniteCache<K, V> dataCache, IgniteBiPredicate<K, V> filter,
                                                                            IgniteModel<Vector, L> mdl,
                                                                            Vectorizer<K, V, C, L> vectorizer,
                                                                            Metric<L> metric) {
        double metricRes;

        try (LabelPairCursor<L> cursor = new CacheBasedLabelPairCursor<>(
            dataCache,
            filter,
            vectorizer,
            mdl
        )) {
            metricRes = metric.score(cursor.iterator());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return metricRes;
    }

    /**
     * Computes the given metric on the given cache.
     *
     * @param dataCache The given cache.
     * @param filter    The given filter.
     * @param mdl       The model.
     * @param metric    The binary classification metric.
     * @param <L>       The type of label.
     * @param <K>       The type of cache entry key.
     * @param <V>       The type of cache entry value.
     * @return Computed metric.
     */
    private static <L, K, V, C extends Serializable> double calculateMetric(Map<K, V> dataCache,
                                                                            IgniteBiPredicate<K, V> filter,
                                                                            IgniteModel<Vector, L> mdl,
                                                                            Vectorizer<K, V, C, L> vectorizer,
                                                                            Metric<L> metric) {
        double metricRes;

        try (LabelPairCursor<L> cursor = new LocalLabelPairCursor<>(
            dataCache,
            filter,
            vectorizer,
            mdl
        )) {
            metricRes = metric.score(cursor.iterator());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return metricRes;
    }
}
