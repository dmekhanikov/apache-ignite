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

package org.apache.ignite.ml.composition.boosting.convergence;

import java.io.Serializable;
import org.apache.ignite.ml.composition.ModelsComposition;
import org.apache.ignite.ml.dataset.DatasetBuilder;
import org.apache.ignite.ml.math.functions.IgniteBiFunction;
import org.apache.ignite.ml.math.functions.IgniteFunction;
import org.apache.ignite.ml.math.functions.IgniteTriFunction;
import org.apache.ignite.ml.math.primitives.vector.Vector;

/**
 * Contains logic of error computing and convergence checking for Gradient Boosting algorithms.
 *
 * @param <K> Type of a key in upstream data.
 * @param <V> Type of a value in upstream data.
 */
public abstract class ConvergenceCheckStrategy<K, V> implements Serializable {
    /** Serial version uid. */
    private static final long serialVersionUID = 710762134746674105L;

    /** Dataset builder. */
    protected DatasetBuilder<K, V> datasetBuilder;

    /** Sample size. */
    private long sampleSize;

    /** External label to internal mapping. */
    private IgniteFunction<Double, Double> externalLbToInternalMapping;

    /** Loss of gradient. */
    private IgniteTriFunction<Long, Double, Double, Double> lossGradient;

    /** Feature extractor. */
    private IgniteBiFunction<K, V, Vector> featureExtractor;

    /** Label extractor. */
    private IgniteBiFunction<K, V, Double> lbExtractor;

    /** Precision of convergence check. */
    private double precision;

    /**
     * Constructs an instance of ConvergenceCheckStrategy.
     *  @param sampleSize Sample size.
     * @param externalLbToInternalMapping External label to internal mapping.
     * @param lossGradient Loss gradient.
     * @param datasetBuilder Dataset builder.
     * @param featureExtractor Feature extractor.
     * @param lbExtractor Label extractor.
     * @param precision
     */
    public ConvergenceCheckStrategy(long sampleSize,
        IgniteFunction<Double, Double> externalLbToInternalMapping,
        IgniteTriFunction<Long, Double, Double, Double> lossGradient,
        DatasetBuilder<K, V> datasetBuilder,
        IgniteBiFunction<K, V, Vector> featureExtractor,
        IgniteBiFunction<K, V, Double> lbExtractor,
        double precision) {

        this.sampleSize = sampleSize;
        this.externalLbToInternalMapping = externalLbToInternalMapping;
        this.lossGradient = lossGradient;
        this.datasetBuilder = datasetBuilder;
        this.featureExtractor = featureExtractor;
        this.lbExtractor = lbExtractor;
        this.precision = precision;
    }

    /**
     * @param currMdl Current model.
     * @return true if GDB is converged.
     */
    public boolean isConverged(ModelsComposition currMdl) {
        double error = computeMeanErrorOnDataset(currMdl);
        assert error >= 0;
        return error < precision;
    }

    /**
     * Compute error for given model on learning dataset.
     * @param mdl Model.
     * @return error mean value.
     */
    protected abstract double computeMeanErrorOnDataset(ModelsComposition mdl);

    /**
     * Compute error on one element of dataset.
     *
     * @param key Key.
     * @param val Value.
     * @param currMdl Current model.
     * @return error.
     */
    public double computeError(K key, V val, ModelsComposition currMdl) {
        Double realAnswer = externalLbToInternalMapping.apply(lbExtractor.apply(key, val));
        Double mdlAnswer = currMdl.apply(featureExtractor.apply(key, val));
        return -lossGradient.apply(sampleSize, realAnswer, mdlAnswer);
    }
}
