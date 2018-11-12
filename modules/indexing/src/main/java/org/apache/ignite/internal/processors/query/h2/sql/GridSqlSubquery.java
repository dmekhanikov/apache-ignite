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

package org.apache.ignite.internal.processors.query.h2.sql;

import org.apache.ignite.internal.sql.ast.GridSqlAst;
import org.apache.ignite.internal.sql.ast.GridSqlElement;

import java.util.ArrayList;


/**
 * Subquery expression.
 */
public class GridSqlSubquery extends GridSqlElement {
    /**
     * @param subQry Subquery.
     */
    public GridSqlSubquery(GridSqlQuery subQry) {
        super(new ArrayList<GridSqlAst>(1));

        addChild(subQry);
    }

    /** {@inheritDoc} */
    @Override public String getSQL() {
        return "(" + subquery().getSQL() + ")";
    }

    /**
     * @return Subquery AST.
     */
    public <X extends GridSqlQuery> X subquery() {
        return child(0);
    }
}