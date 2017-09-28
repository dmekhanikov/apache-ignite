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

package org.apache.ignite.internal.processors.cache.persistence.filename;

import java.io.File;
import java.io.FileFilter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.IgniteLogger;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.configuration.PersistentStoreConfiguration;
import org.apache.ignite.internal.GridKernalContext;
import org.apache.ignite.internal.managers.discovery.GridDiscoveryManager;
import org.apache.ignite.internal.processors.GridProcessorAdapter;
import org.apache.ignite.internal.processors.cache.persistence.GridCacheDatabaseSharedManager;
import org.apache.ignite.internal.util.typedef.internal.A;
import org.apache.ignite.internal.util.typedef.internal.SB;
import org.apache.ignite.internal.util.typedef.internal.U;
import org.apache.ignite.spi.discovery.DiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Component for resolving PDS storage file names, also used for generating consistent ID for case PDS mode is enabled
 */
public class PdsConsistentIdGeneratingFoldersResolver extends GridProcessorAdapter implements PdsFolderResolver {
    /** Database subfolders constant prefix. */
    public static final String DB_FOLDER_PREFIX = "node";

    /** Node index and uid separator in subfolders name. */
    private static final String NODEIDX_UID_SEPARATOR = "-";

    /** Constant node subfolder prefix and node index pattern (nodeII, where II - node index as decimal integer) */
    private static final String NODE_PATTERN = DB_FOLDER_PREFIX + "[0-9]*" + NODEIDX_UID_SEPARATOR;

    /** Uuid as string pattern. */
    private static final String UUID_STR_PATTERN = "[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}";

    /**
     * Subdir (nodeII-UID, where II - node index as decimal integer, UID - string representation of consistent ID)
     * pattern.
     */
    private static final String SUBDIR_PATTERN = NODE_PATTERN + UUID_STR_PATTERN;

    /** Database subfolders for new style filter. */
    public static final FileFilter DB_SUBFOLDERS_NEW_STYLE_FILTER = new FileFilter() {
        @Override public boolean accept(File pathname) {
            return pathname.isDirectory() && pathname.getName().matches(SUBDIR_PATTERN);
        }
    };

    /** Database default folder. */
    public static final String DB_DEFAULT_FOLDER = "db";

    /** Config. */
    private IgniteConfiguration cfg;

    /** Discovery. */
    private GridDiscoveryManager discovery;

    /** Logger. */
    private IgniteLogger log;

    /** Context. */
    private GridKernalContext ctx;

    /** Cached folder settings. */
    private PdsFolderSettings settings;

    /**
     * Creates folders resolver
     * @param ctx Context.
     */
    public PdsConsistentIdGeneratingFoldersResolver(GridKernalContext ctx) {
        super(ctx);
        this.cfg = ctx.config();
        this.discovery = ctx.discovery();
        this.log = ctx.log(PdsFolderResolver.class);
        this.ctx = ctx;
    }

    /**
     * Prepares compatible PDS folder settings. No locking is performed, consistent ID is not overriden
     *
     * @return PDS folder settings compatible with previous versions
     * @param pstStoreBasePath DB storage base path
     */
    private PdsFolderSettings compatibleResolve(final File pstStoreBasePath) {
        return new PdsFolderSettings(pstStoreBasePath, discovery.consistentId(), true);
    }

    /** {@inheritDoc} */
    @Override public PdsFolderSettings resolveFolders() throws IgniteCheckedException {
        if (settings == null) {
            settings = prepareNewSettings();

            if (!settings.isCompatible()) {
                //todo are there any other way to set this value?
                cfg.setConsistentId(settings.consistentId());
            }
        }
        return settings;
    }

    /**
     * Creates new settings when we don't have cached one.
     *
     * @return new settings with prelocked directory (if appropriate).
     * @throws IgniteCheckedException if IO failed.
     */
    private PdsFolderSettings prepareNewSettings() throws IgniteCheckedException {
        final File pstStoreBasePath = resolvePersistentStoreBasePath();
        if (!cfg.isPersistentStoreEnabled())
            return compatibleResolve(pstStoreBasePath);

        // compatible mode from configuration is used fot this case
        if (cfg.getConsistentId() != null) {
            // compatible mode from configuration is used fot this case, no locking, no consitent id change
            return new PdsFolderSettings(pstStoreBasePath, cfg.getConsistentId(), true);
        }
        // The node scans the work directory and checks if there is a folder matching the consistent ID. If such a folder exists, we start up with this ID (compatibility mode)


        // this is required to correctly initialize SPI
        final DiscoverySpi spi = discovery.tryInjectSpi();
        if (spi instanceof TcpDiscoverySpi) {
            final TcpDiscoverySpi tcpDiscoverySpi = (TcpDiscoverySpi)spi;
            final String consistentId = tcpDiscoverySpi.calculateConsistentIdAddrPortBased();
            final String subFolder = U.maskForFileName(consistentId);

            final GridCacheDatabaseSharedManager.FileLockHolder fileLockHolder = tryLock(new File(pstStoreBasePath, subFolder));
            if (fileLockHolder != null)
                return new PdsFolderSettings(pstStoreBasePath,
                    subFolder,
                    consistentId,
                    fileLockHolder,
                    false);

        }

        for (FolderCandidate next : getNodeIndexSortedCandidates(pstStoreBasePath)) {
            final GridCacheDatabaseSharedManager.FileLockHolder fileLockHolder = tryLock(next.file);
            if (fileLockHolder != null) {
                //todo remove debug output
                System.out.println("locked>> " + pstStoreBasePath + " " + next.file);
                return new PdsFolderSettings(pstStoreBasePath, next.file.getName(), next.uuid(), fileLockHolder, false);
            }
        }

        // was not able to find free slot, allocating new
        final GridCacheDatabaseSharedManager.FileLockHolder rootDirLock = lockRootDirectory(pstStoreBasePath);
        try {
            final List<FolderCandidate> sortedCandidates = getNodeIndexSortedCandidates(pstStoreBasePath);
            final int nodeIdx = sortedCandidates.isEmpty() ? 0 : (sortedCandidates.get(sortedCandidates.size() - 1).nodeIndex() + 1);
            return generateAndLockNewDbStorage(pstStoreBasePath, nodeIdx);
        }
        finally {
            rootDirLock.release();
            rootDirLock.close();
        }
    }

    private static String padStart(String str, int minLength, char padChar) {
        A.notNull(str, "String should not be empty");
        if (str.length() >= minLength)
            return str;

        final SB sb = new SB(minLength);

        for (int i = str.length(); i < minLength; ++i)
            sb.a(padChar);

        sb.a(str);
        return sb.toString();

    }
    /**
     * Creates new DB storage folder
     *
     * @param pstStoreBasePath DB root path
     * @param nodeIdx next node index to use in folder name
     * @return new settings to be used in this node
     * @throws IgniteCheckedException if failed
     */
    @NotNull private PdsFolderSettings generateAndLockNewDbStorage(File pstStoreBasePath, int nodeIdx) throws IgniteCheckedException {
        final UUID uuid = UUID.randomUUID();
        final String consIdBasedFolder = genNewStyleSubfolderName(nodeIdx, uuid);
        final File newRandomFolder = U.resolveWorkDirectory(pstStoreBasePath.getAbsolutePath(), consIdBasedFolder, false); //mkdir here
        final GridCacheDatabaseSharedManager.FileLockHolder fileLockHolder = tryLock(newRandomFolder);
        if (fileLockHolder != null) {
            //todo remove debug output
            System.out.println("locked>> " + pstStoreBasePath + " " + consIdBasedFolder);
            return new PdsFolderSettings(pstStoreBasePath, consIdBasedFolder, uuid, fileLockHolder, false);
        }
        throw new IgniteCheckedException("Unable to lock file generated randomly [" + newRandomFolder + "]");
    }

    /**
     * Generates DB subfolder name for provided node index (local) and UUID (consistent ID)
     *
     * @param nodeIdx node index.
     * @param uuid consistent ID.
     * @return folder file name
     */
    @NotNull public static String genNewStyleSubfolderName(final int nodeIdx, final UUID uuid) {
        final String uuidAsStr = uuid.toString();
        assert uuidAsStr.matches(UUID_STR_PATTERN);

        final String nodeIdxPadded = padStart(Integer.toString(nodeIdx), 2, '0');
        return DB_FOLDER_PREFIX + nodeIdxPadded + NODEIDX_UID_SEPARATOR + uuidAsStr;
    }

    /**
     * Acquires lock to root storage directory, used to lock root directory in case creating new files is required.
     * @param pstStoreBasePath rood DB dir to lock
     * @return locked directory, should be released and closed later
     * @throws IgniteCheckedException if failed
     */
    @NotNull private GridCacheDatabaseSharedManager.FileLockHolder lockRootDirectory(
        File pstStoreBasePath) throws IgniteCheckedException {
        GridCacheDatabaseSharedManager.FileLockHolder rootDirLock;
        int retry = 0;
        while ((rootDirLock = tryLock(pstStoreBasePath)) == null) {
            if (retry > 600)
                throw new IgniteCheckedException("Unable to start under DB storage path [" + pstStoreBasePath + "]" +
                    ". Lock is being held to root directory");
            retry++;
        }
        return rootDirLock;
    }

    /**
     * @param pstStoreBasePath root storage folder to scan
     * @return emprty list if there is no files in folder to test.
     * Non null value is returned for folder having applicable new style files.
     * Collection is sorted ascending according to node ID, 0 node index is coming first
     */
    @Nullable private List<FolderCandidate> getNodeIndexSortedCandidates(File pstStoreBasePath) {
        final List<FolderCandidate> res = new ArrayList<>();
        for (File file : pstStoreBasePath.listFiles(DB_SUBFOLDERS_NEW_STYLE_FILTER)) {
            final NodeIndexAndUid nodeIdxAndUid = parseFileName(file);
            if (nodeIdxAndUid == null)
                continue;

            res.add(new FolderCandidate(file, nodeIdxAndUid));
        }
        Collections.sort(res, new Comparator<FolderCandidate>() {
            @Override public int compare(FolderCandidate c1, FolderCandidate c2) {
                return Integer.compare(c1.nodeIndex(), c2.nodeIndex());
            }
        });
        return res;
    }

    /**
     * Tries to lock subfolder within storage root folder
     *
     * @param dbStoreDirWithSubdirectory DB store directory, is to be absolute and should include consistent ID based
     * sub folder
     * @return non null holder if lock was successful, null in case lock failed. If directory does not exist method will
     * always fail to lock.
     */
    private GridCacheDatabaseSharedManager.FileLockHolder tryLock(File dbStoreDirWithSubdirectory) {
        if (!dbStoreDirWithSubdirectory.exists())
            return null;
        final String path = dbStoreDirWithSubdirectory.getAbsolutePath();
        final GridCacheDatabaseSharedManager.FileLockHolder fileLockHolder
            = new GridCacheDatabaseSharedManager.FileLockHolder(path, ctx, log);
        try {
            fileLockHolder.tryLock(1000);
            return fileLockHolder;
        }
        catch (IgniteCheckedException e) {
            U.closeQuiet(fileLockHolder);
            log.info("Unable to acquire lock to file [" + path + "], reason: " + e.getMessage());
            return null;
        }
    }

    /**
     * @return DB storage absolute root path resolved as 'db' folder in Ignite work dir (by default) or using persistent
     * store configuration
     * @throws IgniteCheckedException if I/O failed
     */
    private File resolvePersistentStoreBasePath() throws IgniteCheckedException {
        final PersistentStoreConfiguration pstCfg = cfg.getPersistentStoreConfiguration();

        final File dirToFindOldConsIds;
        if (pstCfg.getPersistentStorePath() != null) {
            File workDir0 = new File(pstCfg.getPersistentStorePath());

            if (!workDir0.isAbsolute())
                dirToFindOldConsIds = U.resolveWorkDirectory(
                    cfg.getWorkDirectory(),
                    pstCfg.getPersistentStorePath(),
                    false
                );
            else
                dirToFindOldConsIds = workDir0;
        }
        else {
            dirToFindOldConsIds = U.resolveWorkDirectory(
                cfg.getWorkDirectory(),
                DB_DEFAULT_FOLDER,
                false
            );
        }
        return dirToFindOldConsIds;
    }

    /**
     * @param subFolderFile new style folder name to parse
     * @return Pair of UUID and node index
     */
    private NodeIndexAndUid parseFileName(@NotNull final File subFolderFile) {
        return parseSubFolderName(subFolderFile, log);
    }

    /**
     * @param file new style file to parse.
     * @param log Logger.
     * @return Pair of UUID and node index.
     */
    @Nullable public static NodeIndexAndUid parseSubFolderName(
        @NotNull File file, IgniteLogger log) {
        final String fileName = file.getName();
        Matcher m = Pattern.compile(NODE_PATTERN).matcher(fileName);
        if (!m.find())
            return null;
        int uidStart = m.end();
        try {
            String uid = fileName.substring(uidStart);
            final UUID uuid = UUID.fromString(uid);
            final String substring = fileName.substring(DB_FOLDER_PREFIX.length(), uidStart - NODEIDX_UID_SEPARATOR.length());
            final int idx = Integer.parseInt(substring);
            return new NodeIndexAndUid(idx, uuid);
        }
        catch (Exception e) {
            log.warning("Unable to parse new style file format: " + e);
            return null;
        }
    }

    /** {@inheritDoc} */
    @Override public void stop(boolean cancel) throws IgniteCheckedException {
        if (settings != null) {
            final GridCacheDatabaseSharedManager.FileLockHolder fileLockHolder = settings.getLockedFileLockHolder();
            if (fileLockHolder != null) {
                fileLockHolder.release();
                fileLockHolder.close();
            }
        }
        super.stop(cancel);
    }

    public static class FolderCandidate {

        private final File file;
        private final NodeIndexAndUid params;

        public FolderCandidate(File file, NodeIndexAndUid params) {

            this.file = file;
            this.params = params;
        }

        public int nodeIndex() {
            return params.nodeIndex();
        }

        public Serializable uuid() {
            return params.uuid();
        }
    }

    public static class NodeIndexAndUid {
        private final int nodeIndex;
        private final UUID uuid;

        public NodeIndexAndUid(int nodeIndex, UUID nodeConsistentId) {
            this.nodeIndex = nodeIndex;
            this.uuid = nodeConsistentId;
        }

        public int nodeIndex() {
            return nodeIndex;
        }

        public Serializable uuid() {
            return uuid;
        }
    }

}


