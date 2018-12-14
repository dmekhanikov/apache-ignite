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

package org.apache.ignite.ml.util.generators.function;

import java.util.Arrays;
import java.util.List;
import org.apache.ignite.ml.math.functions.IgniteFunction;
import org.apache.ignite.ml.math.primitives.vector.Vector;
import org.apache.ignite.ml.math.primitives.vector.VectorUtils;
import org.apache.ignite.ml.util.generators.variable.RandomProducer;
import org.apache.ignite.ml.util.generators.variable.UniformRandomProducer;
import org.apache.ignite.ml.util.generators.variable.VectorGenerator;

public class ParametricVectorGenerator implements VectorGenerator {
    private final List<IgniteFunction<Double, Double>> perDimensionGenerators;
    private final RandomProducer randomProducer;

    public ParametricVectorGenerator(RandomProducer parameterGenerator,
        IgniteFunction<Double, Double> ... perDimensionGenerators) {

        this.perDimensionGenerators = Arrays.asList(perDimensionGenerators);
        this.randomProducer = parameterGenerator;
    }

    @Override public Vector get() {
        Double t = randomProducer.get();
        return VectorUtils.of(
            perDimensionGenerators.stream()
                .mapToDouble(f -> f.apply(t))
                .toArray()
        );
    }
}
