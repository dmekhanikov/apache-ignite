/*
 * Copyright 2019 GridGain Systems, Inc. and Contributors.
 *
 * Licensed under the GridGain Community Edition License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.gridgain.com/products/software/community-edition/gridgain-community-edition-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.internal.util.nio;

import java.net.InetSocketAddress;
import java.security.cert.Certificate;

import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.IgniteException;
import org.apache.ignite.lang.IgniteInClosure;
import org.jetbrains.annotations.Nullable;

/**
 * This interface represents established or closed connection between nio server and remote client.
 */
public interface GridNioSession {
    /** */
    final long DEFAILT_IDLE_TIMEOUT = -1;

    /**
     * Gets local address of this session.
     *
     * @return Local network address or {@code null} if non-socket communication is used.
     */
    @Nullable public InetSocketAddress localAddress();

    /**
     * Gets address of remote peer on this session.
     *
     * @return Address of remote peer or {@code null} if non-socket communication is used.
     */
    @Nullable public InetSocketAddress remoteAddress();

    /**
     * Gets session idle tiomeout.
     *
     * @return Total count of bytes sent.
     */
    public default long idleTimeout() {
        return DEFAILT_IDLE_TIMEOUT;
    }

    /**
     * Gets the total count of bytes sent since the session was created.
     *
     * @return Total count of bytes sent.
     */
    public long bytesSent();

    /**
     * Gets the total count of bytes received since the session was created.
     *
     * @return Total count of bytes received.
     */
    public long bytesReceived();

    /**
     * Gets the time when the session was created.
     *
     * @return Time when this session was created returned by {@link System#currentTimeMillis()}.
     */
    public long createTime();

    /**
     * If session is closed, this method will return session close time returned by {@link System#currentTimeMillis()}.
     * If session is not closed, this method will return {@code 0}.
     *
     * @return Session close time.
     */
    public long closeTime();

    /**
     * Returns the time when last read activity was performed on this session.
     *
     * @return Lats receive time.
     */
    public long lastReceiveTime();

    /**
     * Returns time when last send activity was performed on this session.
     *
     * @return Last send time.
     */
    public long lastSendTime();

    /**
     * Returns time when last send was scheduled on this session.
     *
     * @return Last send schedule time.
     */
    public long lastSendScheduleTime();

    /**
     * Performs a request for asynchronous session close.
     *
     * @return Future representing result.
     */
    public default GridNioFuture<Boolean> close() {
        return close(null);
    }

    /**
     * Performs a request for asynchronous session close.
     *
     * @param cause Optional close cause.
     * @return Future representing result.
     */
    public GridNioFuture<Boolean> close(@Nullable IgniteCheckedException cause);

    /**
     * Performs a request for asynchronous data send.
     *
     * @param msg Message to be sent. This message will be eventually passed in to a parser plugged
     *            to the nio server.
     * @return Future representing result.
     */
    public default GridNioFuture<?> send(Object msg) {
        return send(msg, null);
    }

    /**
     * @param msg Message to be sent.
     * @param ackC Optional closure invoked when ack for message is received.
     */
    public GridNioFuture<?> send(Object msg, @Nullable IgniteInClosure<IgniteException> ackC);

    /**
     * Performs a request for asynchronous data send.
     *
     * @param msg Message to be sent. This message will be eventually passed in to a parser plugged
     *            to the nio server.
     * @throws IgniteCheckedException If failed.
     */
    public default void sendNoFuture(Object msg) throws IgniteCheckedException {
        sendNoFuture(msg, null);
    }

    /**
     * @param msg Message to be sent.
     * @param ackC Optional closure invoked when ack for message is received.
     * @throws IgniteCheckedException If failed.
     */
    public void sendNoFuture(Object msg, @Nullable IgniteInClosure<IgniteException> ackC)
        throws IgniteCheckedException;

    /**
     * Gets metadata associated with specified key.
     *
     * @param key Key to look up.
     * @return Associated meta object or {@code null} if meta was not found.
     */
    @Nullable public <T> T meta(int key);

    /**
     * Adds metadata associated with specified key.
     *
     * @param key Metadata Key.
     * @param val Metadata value.
     * @return Previously associated object or {@code null} if no objects were associated.
     */
    @Nullable public <T> T addMeta(int key, @Nullable T val);

    /**
     * Removes metadata with the specified key.
     *
     * @param key Metadata key.
     * @return Object that was associated with the key or {@code null}.
     */
    @Nullable public <T> T removeMeta(int key);

    /**
     * @return {@code True} if this connection was initiated from remote node.
     */
    public boolean accepted();

    /**
     * @return Client SSL certificates
     */
    @Nullable public Certificate[] certificates();

    /**
     * Resumes session reads.
     *
     * @return Future representing result.
     */
    public GridNioFuture<?> resumeReads();

    /**
     * Pauses reads.
     *
     * @return Future representing result.
     */
    public GridNioFuture<?> pauseReads();

    /**
     * @param recoveryDesc Recovery descriptor.
     */
    public void outRecoveryDescriptor(GridNioRecoveryDescriptor recoveryDesc);

    /**
     * @param recoveryDesc Recovery descriptor.
     */
    public void inRecoveryDescriptor(GridNioRecoveryDescriptor recoveryDesc);

    /**
     * @return Recovery descriptor if recovery is supported, {@code null otherwise.}
     */
    @Nullable public GridNioRecoveryDescriptor outRecoveryDescriptor();

    /**
     * @return Recovery descriptor if recovery is supported, {@code null otherwise.}
     */
    @Nullable public GridNioRecoveryDescriptor inRecoveryDescriptor();
}