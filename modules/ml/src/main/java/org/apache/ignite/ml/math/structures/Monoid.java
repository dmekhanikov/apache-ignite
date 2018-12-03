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

package org.apache.ignite.ml.math.structures;

import org.apache.ignite.ml.math.functions.IgniteBinaryOperator;
import org.apache.ignite.ml.math.primitives.vector.Vector;
import org.apache.ignite.ml.math.primitives.vector.VectorUtils;
import org.apache.ignite.ml.math.primitives.vector.impl.DenseVector;

public interface Monoid<Self extends Monoid<? super Self>> {
    public Self mappend(Self other);

    public Self mempty();

    public static <T> Monoid<MonoidWrapper<T>> asMonoid(T t, IgniteBinaryOperator<T> mappendFunc, T mempty) {
        return new MonoidWrapper<>(t, mappendFunc, mempty);
    }

    public static Monoid<MonoidWrapper<Vector>> vectorMonoid(Vector v) {
        return asMonoid(v, VectorUtils::concat, new DenseVector(0));
    }
}
