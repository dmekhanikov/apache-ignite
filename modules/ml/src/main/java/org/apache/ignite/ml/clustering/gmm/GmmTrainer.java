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

package org.apache.ignite.ml.clustering.gmm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import org.apache.ignite.internal.util.typedef.internal.A;
import org.apache.ignite.ml.dataset.Dataset;
import org.apache.ignite.ml.dataset.DatasetBuilder;
import org.apache.ignite.ml.dataset.primitive.builder.context.EmptyContextBuilder;
import org.apache.ignite.ml.dataset.primitive.context.EmptyContext;
import org.apache.ignite.ml.environment.LearningEnvironment;
import org.apache.ignite.ml.environment.LearningEnvironmentBuilder;
import org.apache.ignite.ml.environment.logging.MLLogger;
import org.apache.ignite.ml.math.functions.IgniteBiFunction;
import org.apache.ignite.ml.math.functions.IgniteBinaryOperator;
import org.apache.ignite.ml.math.primitives.matrix.Matrix;
import org.apache.ignite.ml.math.primitives.vector.Vector;
import org.apache.ignite.ml.math.primitives.vector.VectorUtils;
import org.apache.ignite.ml.math.stat.MultivariateGaussianDistribution;
import org.apache.ignite.ml.trainers.DatasetTrainer;
import org.apache.ignite.ml.trainers.FeatureLabelExtractor;

/**
 * Traner for GMM model.
 */
public class GmmTrainer extends DatasetTrainer<GmmModel, Double> {
    /** Min divergence of mean vectors beween iterations. If divergence will less then trainer stops. */
    private double eps = 1e-3;

    /** Count of components. */
    private int countOfComponents = 2;

    /** Max count of iterations. */
    private int maxCountOfIterations = 10;

    /** Seed. */
    private long seed = System.currentTimeMillis();

    /** Initial means. */
    private List<Vector> initialMeans;

    /**
     * Creates an instance of GmmTrainer.
     *
     * @param countOfComponents Count of components.
     * @param maxCountOfIterations Max count of iterations.
     */
    public GmmTrainer(int countOfComponents, int maxCountOfIterations) {
        this.countOfComponents = countOfComponents;
        this.maxCountOfIterations = maxCountOfIterations;
    }

    /** {@inheritDoc} */
    @Override
    public <K, V> GmmModel fit(DatasetBuilder<K, V> datasetBuilder, FeatureLabelExtractor<K, V, Double> extractor) {
        return updateModel(null, datasetBuilder, extractor);
    }

    /**
     * Sets seed.
     *
     * @param seed Seed.
     * @return trainer.
     */
    public GmmTrainer withSeed(long seed) {
        this.seed = seed;
        return this;
    }

    /**
     * Sets numberOfComponents.
     *
     * @param numberOfComponents Number of components.
     * @return trainer.
     */
    public GmmTrainer withCountOfComponents(int numberOfComponents) {
        A.ensure(numberOfComponents > 0, "Number of components in GMM cannot equal 0");

        this.countOfComponents = numberOfComponents;
        initialMeans = null;
        return this;
    }

    /**
     * Sets initial means.
     *
     * @param means Initial means for clusters.
     * @return trainer.
     */
    public GmmTrainer withInitialMeans(List<Vector> means) {
        A.notEmpty(means, "GMM should starts with non empty initial components list");

        this.initialMeans = means;
        this.countOfComponents = means.size();
        return this;
    }

    /**
     * Sets max count of iterations
     *
     * @param maxCountOfIterations Max count of iterations.
     * @return trainer.
     */
    public GmmTrainer withMaxCountIterations(int maxCountOfIterations) {
        A.ensure(maxCountOfIterations > 0, "Max count iterations cannot be less or equal zero");

        this.maxCountOfIterations = maxCountOfIterations;
        return this;
    }

    /**
     * Sets min divergence beween iterations.
     *
     * @param eps Eps.
     * @return trainer.
     */
    public GmmTrainer withEps(double eps) {
        A.ensure(eps > 0 && eps < 1.0, "Min divergence beween iterations should be between 0.0 and 1.0");

        this.eps = eps;
        return this;
    }

    /**
     * Trains model based on the specified data.
     *
     * @param dataset Dataset.
     * @return GMM model.
     */
    private GmmModel fit(Dataset<EmptyContext, GmmPartitionData> dataset) {
        GmmModel model = init(dataset);
        boolean isConverged = false;
        int countOfIterations = 0;
        while (!isConverged) {
            MeanWithClusterProbAggregator.AggregatedStats stats = MeanWithClusterProbAggregator.aggreateStats(dataset);
            Vector clusterProbs = stats.clusterProbabilities();
            List<Vector> newMeans = stats.means();

            A.ensure(newMeans.size() == model.countOfComponents(), "newMeans.size() == count of components");
            A.ensure(newMeans.get(0).size() == initialMeans.get(0).size(), "newMeans[0].size() == initialMeans[0].size()");
            List<Matrix> newCovs = CovarianceMatricesAggregator.computeCovariances(dataset, clusterProbs, newMeans);

            List<MultivariateGaussianDistribution> components = buildComponents(newMeans, newCovs);
            GmmModel newModel = new GmmModel(clusterProbs, components);

            countOfIterations += 1;
            isConverged = isConverged(model, newModel) || countOfIterations > maxCountOfIterations;
            model = newModel;

            if (!isConverged)
                dataset.compute(data -> GmmPartitionData.updatePcxi(data, clusterProbs, components));
        }

        return model;
    }

    /**
     * Init means and covariances.
     *
     * @param dataset Dataset.
     * @return initial model.
     */
    private GmmModel init(Dataset<EmptyContext, GmmPartitionData> dataset) {
        if (initialMeans == null) {
            initialMeans = dataset.compute(
                selectNRandomXsMapper(countOfComponents, seed),
                selectNRandomXsReducer(countOfComponents, seed)
            );
        }

        dataset.compute(data -> GmmPartitionData.estimateLikelihoodClusters(data, initialMeans));

        List<Matrix> initialCovs = CovarianceMatricesAggregator.computeCovariances(
            dataset,
            VectorUtils.of(DoubleStream.generate(() -> 1. / countOfComponents).limit(countOfComponents).toArray()),
            initialMeans);

        List<MultivariateGaussianDistribution> distributions = new ArrayList<>();
        for (int i = 0; i < countOfComponents; i++)
            distributions.add(new MultivariateGaussianDistribution(initialMeans.get(i), initialCovs.get(i)));

        return new GmmModel(
            VectorUtils.of(DoubleStream.generate(() -> 1. / countOfComponents).limit(countOfComponents).toArray()),
            distributions
        );
    }

    /**
     * Create new model components with provided means and covariances.
     *
     * @param means Means.
     * @param covs Covariances.
     * @return gmm components.
     */
    private List<MultivariateGaussianDistribution> buildComponents(List<Vector> means, List<Matrix> covs) {
        A.ensure(means.size() == covs.size(), "means.size() == covs.size()");

        List<MultivariateGaussianDistribution> res = new ArrayList<>();
        for (int i = 0; i < means.size(); i++)
            res.add(new MultivariateGaussianDistribution(means.get(i), covs.get(i)));

        return res;
    }

    /**
     * Check algorithm covergency. If it's true then algorithm stops.
     *
     * @param oldModel Old model.
     * @param newModel New model.
     */
    private boolean isConverged(GmmModel oldModel, GmmModel newModel) {
        A.ensure(oldModel.countOfComponents() == newModel.countOfComponents(),
            "oldModel.countOfComponents() == newModel.countOfComponents()");

        boolean isConverged = true;
        for (int i = 0; i < oldModel.countOfComponents(); i++) {
            MultivariateGaussianDistribution d1 = oldModel.distributions().get(i);
            MultivariateGaussianDistribution d2 = newModel.distributions().get(i);

            isConverged = isConverged && Math.sqrt(d1.mean().getDistanceSquared(d2.mean())) < eps;
        }

        return isConverged;
    }

    /** {@inheritDoc} */
    @Override public boolean isUpdateable(GmmModel mdl) {
        return mdl.countOfComponents() == countOfComponents;
    }

    /** {@inheritDoc} */
    @Override protected <K, V> GmmModel updateModel(GmmModel mdl, DatasetBuilder<K, V> datasetBuilder,
        FeatureLabelExtractor<K, V, Double> extractor) {

        try (Dataset<EmptyContext, GmmPartitionData> dataset = datasetBuilder.build(
            LearningEnvironmentBuilder.defaultBuilder(),
            new EmptyContextBuilder<>(),
            new GmmPartitionData.Builder<>(extractor, countOfComponents)
        )) {
            if (mdl != null) {
                if (initialMeans != null)
                    environment.logger().log(MLLogger.VerboseLevel.HIGH, "Initial means will be replaced by model from update");
                initialMeans = mdl.distributions().stream().map(x -> x.mean()).collect(Collectors.toList());
            }

            return fit(dataset);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns mapper for initial means selection.
     *
     * @param n Number of components.
     * @return mapper.
     */
    private static IgniteBiFunction<GmmPartitionData, LearningEnvironment, List<Vector>> selectNRandomXsMapper(int n,
        long seed) {
        return (data, env) -> new Random(seed).ints(n, 0, data.size())
            .mapToObj(data::getX)
            .collect(Collectors.toList());
    }

    /**
     * Returns reducer for means selection.
     *
     * @param n Number of components.
     * @param seed Seed.
     * @return reducer.
     */
    private static IgniteBinaryOperator<List<Vector>> selectNRandomXsReducer(int n, long seed) {
        return (l, r) -> {
            A.ensure(l != null || r != null, "l != null || r != null");
            if (l == null)
                return checkList(n, r);
            if (r == null)
                return checkList(n, l);

            List<Vector> res = new ArrayList<>();
            res.addAll(l);
            res.addAll(r);
            Collections.shuffle(res, new Random(seed));

            return res.stream().limit(n).collect(Collectors.toList());
        };
    }

    /**
     * @param expectedSize Expected size.
     * @param xs Xs.
     */
    private static List<Vector> checkList(int expectedSize, List<Vector> xs) {
        A.ensure(xs.size() == expectedSize, "xs.size() == expectedSize");
        return xs;
    }
}
