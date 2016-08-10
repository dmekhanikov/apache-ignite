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

package org.apache.ignite.internal.pagemem.store;

import org.apache.ignite.IgniteCheckedException;

import java.nio.ByteBuffer;

/**
 * Persistent store of pages.
 */
public interface PageStore {
    /**
     * Checks if page exists.
     *
     * @return {@code True} if page exists.
     * @throws IgniteCheckedException If failed.
     */
    public boolean exists();

    /**
     * Allocates next page index.
     *
     * @return Next page index.
     * @throws IgniteCheckedException If failed to allocate.
     */
    public long allocatePage() throws IgniteCheckedException;

    /**
     * Gets number of allocated pages.
     *
     * @return Number of allocated pages.
     */
    public int pages();

    /**
     * Reads a page.
     *
     * @param pageId Page ID.
     * @param pageBuf Page buffer to read into.
     * @throws IgniteCheckedException If reading failed (IO error occurred).
     */
    public void read(long pageId, ByteBuffer pageBuf) throws IgniteCheckedException;

    /**
     * Reads a header.
     *
     * @param buf Buffer to write to.
     * @throws IgniteCheckedException If failed.
     */
    public void readHeader(ByteBuffer buf) throws IgniteCheckedException;

    /**
     * Writes a page.
     *
     * @param pageId Page ID.
     * @param pageBuf Page buffer to write.
     * @throws IgniteCheckedException If page writing failed (IO error occurred).
     */
    public void write(long pageId, ByteBuffer pageBuf) throws IgniteCheckedException;

    /**
     * Gets page offset within the store file.
     *
     * @param pageId Page ID.
     * @return Page offset.
     */
    public long pageOffset(long pageId);

    /**
     * Sync method used to ensure that the given pages are guaranteed to be written to the store.
     *
     * @throws IgniteCheckedException If sync failed (IO error occurred).
     */
    public void sync() throws IgniteCheckedException;
}
