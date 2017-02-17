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

package org.apache.ignite.math.impls;

import org.apache.ignite.math.*;
import org.apache.ignite.math.impls.storage.*;

/**
 * Constant value, read-only vector.
 */
public class ConstantVector extends AbstractVector {
    /**
     *
     */
    public ConstantVector() {
        // No-op.
    }

    /**
     *
     * @param size
     * @param val
     */
    public ConstantVector(int size, double val) {
        super(true, new ConstantVectorStorage(size, val));
    }

    private ConstantVectorStorage storage() {
        return (ConstantVectorStorage)getStorage();
    }

    @Override
    public Vector copy() {
        return new ConstantVector(storage().size(), storage().constant());
    }

    @Override
    public Vector like(int crd) {
        return new ConstantVector(crd, storage().constant());
    }

    @Override
    public Matrix likeMatrix(int rows, int cols) {
        return null; // TODO
    }

    @Override
    public Matrix toMatrix(boolean row) {
        return null; // TODO
    }

    @Override
    public Matrix toMatrixPlusOne(boolean row, double zeroVal) {
        return null; // TODO
    }
}
