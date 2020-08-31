/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.internal.commandline;

import java.util.logging.Logger;
import org.apache.ignite.internal.client.GridClient;
import org.apache.ignite.internal.client.GridClientConfiguration;
import org.apache.ignite.internal.client.impl.GridClientFutureAdapter;
import org.apache.ignite.internal.client.impl.GridClientImpl;
import org.apache.ignite.internal.client.impl.connection.GridClientConnectionManagerOsImpl;
import org.apache.ignite.internal.client.impl.connection.GridClientNioTcpConnection;
import org.apache.ignite.internal.client.impl.connection.GridClientNioTcpConnection.TcpClientFuture;
import org.apache.ignite.internal.processors.rest.client.message.GridClientClusterNameRequest;

/**
 * Command for interacting with warm-up.
 */
public class WarmUpCommand implements Command<Void> {
    /** {@inheritDoc} */
    @Override public void printUsage(Logger logger) {
        // TODO: 27.08.2020 Add.
    }

    /** {@inheritDoc} */
    @Override public Void arg() {
        return null;
    }

    /** {@inheritDoc} */
    @Override public String name() {
        return CommandList.WARM_UP.toCommandName();
    }

    /** {@inheritDoc} */
    @Override public Object execute(GridClientConfiguration clientCfg, Logger logger) throws Exception {
        try (GridClient client = Command.startClient(clientCfg, true)) {

            GridClientImpl client1 = (GridClientImpl)client;
            GridClientConnectionManagerOsImpl connMgr = (GridClientConnectionManagerOsImpl)client1.connectionManager();
            GridClientNioTcpConnection conn = (GridClientNioTcpConnection)connMgr.conns.values().iterator().next();

            TcpClientFuture<Object> fut = new TcpClientFuture<>();

            GridClientFutureAdapter<Object> resFut = conn.makeRequest(new GridClientClusterNameRequest(), fut);

            resFut.get();

            System.out.println(conn);

            // TODO: 28.08.2020 Implement.
        }
        return null;
    }
}
