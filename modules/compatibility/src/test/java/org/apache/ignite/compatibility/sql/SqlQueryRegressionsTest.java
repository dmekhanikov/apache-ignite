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
package org.apache.ignite.compatibility.sql;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.QueryEntity;
import org.apache.ignite.cache.query.SqlFieldsQuery;
import org.apache.ignite.compatibility.sql.model.ModelFactory;
import org.apache.ignite.compatibility.sql.model.Person;
import org.apache.ignite.compatibility.testframework.junits.Dependency;
import org.apache.ignite.compatibility.testframework.junits.IgniteCompatibilityAbstractTest;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.ClientConnectorConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.internal.IgniteEx;
import org.apache.ignite.internal.IgnitionEx;
import org.apache.ignite.internal.util.typedef.internal.U;
import org.apache.ignite.lang.IgniteInClosure;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.TcpDiscoveryIpFinder;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.apache.ignite.testframework.junits.multijvm.IgniteProcessProxy;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

/**
 * TODO: Add class description.
 */
@SuppressWarnings("TypeMayBeWeakened")
public class SqlQueryRegressionsTest extends IgniteCompatibilityAbstractTest {
    /** Ignite version. */
    private static final String IGNITE_VERSION = "2.5.0";

    /** */
    private static final int OLD_JDBC_PORT = 10800;

    /** */
    private static final int NEW_JDBC_PORT = 10801;

    /** Query workers count. */
    private static final int WORKERS_CNT = 1;

    /** */
    private static final long TEST_TIMEOUT = 10_000;

    /** */
    private static final long WARM_UP_TIMEOUT = 5_000;

    private static final String JDBC_URL = "jdbc:ignite:thin://127.0.0.1:";

    /** */
    public static final TcpDiscoveryIpFinder OLD_VER_FINDER = new TcpDiscoveryVmIpFinder(true) {{
        setAddresses(Collections.singleton("127.0.0.1:47500..47509"));
    }};

    /**
     *
     */
    public static final TcpDiscoveryVmIpFinder NEW_VER_FINDER = new TcpDiscoveryVmIpFinder(true) {{
        setAddresses(Collections.singleton("127.0.0.1:47510..47519"));
    }};

    /**
     * @throws Exception If failed.
     */
    @Test
    public void testSqlPerformanceRegressions() throws Exception {
        try {
            startOldAndNewClusters();

            populateData();

            Supplier<String> qrysSupplier = new PredefinedQueriesSupplier(Arrays.asList(
                "SELECT * FROM person p1, person p2",
                "SELECT * FROM person p1"
            ));

            try (SimpleConnectionPool oldConnPool = new SimpleConnectionPool(JDBC_URL, OLD_JDBC_PORT, WORKERS_CNT);
                 SimpleConnectionPool newConnPool = new SimpleConnectionPool(JDBC_URL, NEW_JDBC_PORT, WORKERS_CNT)) {
                // 0. Warm-up.
                runBenchmark(WARM_UP_TIMEOUT, oldConnPool, newConnPool, qrysSupplier);

                // 1. Initial run.
                Collection<QueryDuelResult> suspiciousQrys =
                    runBenchmark(WARM_UP_TIMEOUT, oldConnPool, newConnPool, qrysSupplier);

                if (suspiciousQrys.isEmpty())
                    return; // No suspicious queries - no problem.

                Set<String> problematicQrys = suspiciousQrys.stream()
                    .map(QueryDuelResult::query)
                    .collect(Collectors.toSet());

                if (log.isInfoEnabled())
                    log.info("proble");

                Supplier<String> problematicQrysSupplier = new PredefinedQueriesSupplier(problematicQrys);

                // 2. Rerun problematic queries to ensure they are not outliers.
                Collection<QueryDuelResult> failedQueries =
                    runBenchmark(WARM_UP_TIMEOUT, oldConnPool, newConnPool, problematicQrysSupplier);

                assertTrue("Some SQL queries are slower in the current version than in the previous one: "
                    + failedQueries, failedQueries.isEmpty());
            }
        }
        finally {
            stopClusters();
        }
    }

    @Override protected long getTestTimeout() {
        return  2 * TEST_TIMEOUT + WARM_UP_TIMEOUT + super.getTestTimeout();
    }

    public Collection<QueryDuelResult> runBenchmark(final long timeout, SimpleConnectionPool oldConnPool,
        SimpleConnectionPool newConnPool, Supplier<String> qrySupplier) throws InterruptedException {
        Collection<QueryDuelResult> suspiciousQrys = Collections.newSetFromMap(new ConcurrentHashMap<>());

        final long end = System.currentTimeMillis() + timeout;

        BlockingExecutor exec = new BlockingExecutor(WORKERS_CNT);

        while (System.currentTimeMillis() < end) {
            QueryDuelRunner runner =
                new QueryDuelRunner(oldConnPool, newConnPool, qrySupplier , suspiciousQrys);

            exec.execute(runner);
        }

        exec.stopAndWaitForTermination();

        return suspiciousQrys;
    }

    public void startOldAndNewClusters() throws Exception {
        // Old cluster.
        startGrid(1, IGNITE_VERSION, new NodeConfigurationClosure(), new PostStartupClosure(true));

        // New cluster
        IgnitionEx.start(prepareNodeConfig(
            getConfiguration(getTestIgniteInstanceName(0)), NEW_VER_FINDER, NEW_JDBC_PORT));
    }

    public void populateData() throws SQLException {
        initializeSchema(grid(0));
        // TODO use streamer.
//        try (Connection oldConn = DriverManager.getConnection("jdbc:ignite:thin://127.0.0.1:" + OLD_JDBC_PORT);
//             Connection newConn = DriverManager.getConnection("jdbc:ignite:thin://127.0.0.1:" + NEW_JDBC_PORT)) {
//            System.out.println("populateData(oldConn);");
//            populateData(oldConn);
//            System.out.println("populateData(newConn);");
//            populateData(newConn);
//        }
    }

    public void stopClusters() {
        IgniteProcessProxy.killAll();
        stopGrid(0);
    }

//    try (Statement stmt = oldConn.createStatement()) {
//            stmt.execute("CREATE TABLE person (id BIGINT PRIMARY KEY, name VARCHAR) " +
//                "WITH \"cache_name=PERSON_CACHE\"");
//
//            stmt.execute("INSERT INTO person VALUES (1, 'KOKO')");
//            stmt.execute("INSERT INTO person VALUES (2, 'BEBE')");
//        }
//        try (PreparedStatement stmt = oldConn.prepareStatement("SELECT * FROM person")) {
//            try (ResultSet rs = stmt.executeQuery()) {
//                while (rs.next()) {
//                    System.out.println("RS=" + rs.getInt(1) + ", " + rs.getString(2));
//                }
//            }
//        }

    /**
     * Run SQL statement on specified node.
     *
     * @param node node to execute query.
     * @param stmt Statement to run.
     * @param args arguments of statements
     * @return Run result.
     */
    private static List<List<?>> executeSql(IgniteEx node, String stmt, Object... args) {
        return node.context().query().querySqlFields(new SqlFieldsQuery(stmt).setArgs(args), true).getAll();
    }

    /** {@inheritDoc} */
    @Override @NotNull protected Collection<Dependency> getDependencies(String igniteVer) {
        Collection<Dependency> dependencies = super.getDependencies(igniteVer);

        dependencies.add(new Dependency("indexing", "org.apache.ignite", "ignite-indexing", IGNITE_VERSION, false));

        // TODO add and exclude proper versions of h2
        dependencies.add(new Dependency("h2", "com.h2database", "h2", "1.4.195", false));

        return dependencies;
    }

    /** {@inheritDoc} */
    @Override protected Set<String> getExcluded(String ver, Collection<Dependency> dependencies) {
        Set<String> excluded = super.getExcluded(ver, dependencies);

        excluded.add("h2");

        return excluded;
    }

    /**
     *
     */
    private static class PostStartupClosure implements IgniteInClosure<Ignite> {

        /**
         *
         */
        boolean createTable;

        /**
         * @param createTable {@code true} In case table should be created
         */
        public PostStartupClosure(boolean createTable) {
            this.createTable = createTable;
        }

        /** {@inheritDoc} */
        @Override public void apply(Ignite ignite) {
            if (createTable) {
                initializeSchema(ignite);
            }
        }

    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void initializeSchema(Ignite ignite) {
        ModelFactory factory = new Person.PersonFactory(0);
        QueryEntity qryEntity = factory.queryEntity();
        CacheConfiguration cacheCfg = new CacheConfiguration<>(factory.tableName())
            .setQueryEntities(Collections.singleton(qryEntity)).setSqlSchema("PUBLIC");

        IgniteCache personCache = ignite.createCache(cacheCfg);

        for (long i = 0; i < 100; i++)
            personCache.put(i, factory.createRandom());
    }

    /**
     *
     */
    private static class NodeConfigurationClosure implements IgniteInClosure<IgniteConfiguration> {
        /** {@inheritDoc} */
        @Override public void apply(IgniteConfiguration cfg) {
            prepareNodeConfig(cfg, OLD_VER_FINDER, OLD_JDBC_PORT);
        }
    }

    private static IgniteConfiguration prepareNodeConfig(IgniteConfiguration cfg, TcpDiscoveryIpFinder ipFinder,
        int jdbcPort) {
        cfg.setLocalHost("127.0.0.1");
        cfg.setPeerClassLoadingEnabled(false);

        TcpDiscoverySpi disco = new TcpDiscoverySpi();
        disco.setIpFinder(ipFinder);
        cfg.setDiscoverySpi(disco);

        ClientConnectorConfiguration clientCfg = new ClientConnectorConfiguration();
        clientCfg.setPort(jdbcPort);
        return cfg;
    }

    private static class QueryDuelRunner implements Runnable {
        private final SimpleConnectionPool oldConnPool;
        private final SimpleConnectionPool newConnPool;
        private final Supplier<String> qrySupplier;
        private final Collection<QueryDuelResult> suspiciousQrs;

        private QueryDuelRunner(SimpleConnectionPool oldConnPool, SimpleConnectionPool newConnPool,
            Supplier<String> qrySupplier, Collection<QueryDuelResult> suspiciousQrs) {
            this.oldConnPool = oldConnPool;
            this.newConnPool = newConnPool;
            this.qrySupplier = qrySupplier;
            this.suspiciousQrs = suspiciousQrs;
        }

        @Override public void run() {
            String qry = qrySupplier.get();

            try {
                QueryExecutionTimer oldVerRun = new QueryExecutionTimer(qry, oldConnPool);
                QueryExecutionTimer newVerRun = new QueryExecutionTimer(qry, newConnPool);

                CompletableFuture<Long> oldVerFut = CompletableFuture.supplyAsync(oldVerRun);
                CompletableFuture<Long> newVerFut = CompletableFuture.supplyAsync(newVerRun);

                CompletableFuture.allOf(oldVerFut, newVerFut).get();

                Long oldRes = oldVerFut.get();
                Long newRes = newVerFut.get();

                System.out.println("newRes=" + newRes + ", oldRes=" + oldRes + ", diff=" + (newRes - oldRes));

                if (oldRes < newRes) { // TODO Good suspicious query condition.
                    suspiciousQrs.add(new QueryDuelResult(qry, oldRes, newRes, null));
                }
            }
            catch (Exception e) {
                e.printStackTrace();

                suspiciousQrs.add(new QueryDuelResult(qry, -1, -1, e));
            }
        }
    }

    private static class QueryDuelResult {
        private final String qry;
        private final long oldQryExecTime;
        private final long newQryExecTime;
        private final Exception err;

        QueryDuelResult(String qry, long oldQryExecTime, long newQryExecTime, Exception err) {
            this.qry = qry;
            this.oldQryExecTime = oldQryExecTime;
            this.newQryExecTime = newQryExecTime;
            this.err = err;
        }

        public String query() {
            return qry;
        }

        public long oldExecutionTime() {
            return oldQryExecTime;
        }

        public long newExecutionTime() {
            return newQryExecTime;
        }

        public Exception error() {
            return err;
        }

        @Override public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            QueryDuelResult res = (QueryDuelResult)o;
            return Objects.equals(qry, res.qry);
        }

        @Override public int hashCode() {
            return Objects.hash(qry);
        }

        @Override public String toString() {
            return "QueryDuelResult{" +
                "qry='" + qry + '\'' +
                ", oldQryExecTime=" + oldQryExecTime +
                ", newQryExecTime=" + newQryExecTime +
                ", err=" + err +
                '}';
        }
    }

    private static class QueryExecutionTimer implements Supplier<Long> {
        private final String qry;
        private final SimpleConnectionPool connPool; // TODO check query result.

        private QueryExecutionTimer(String qry, SimpleConnectionPool connPool) {
            this.qry = qry;
            this.connPool = connPool;
        }

        @Override public Long get() {
            long start = System.currentTimeMillis();

            Connection conn = connPool.getConnection();

            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery(qry)) { // "SELECT * FROM person"
                    while (rs.next()) { // TODO check for empty result
                        //  System.out.println("RS=" + rs.getString(1) + ", " + rs.getInt(2));
                    }
                }
            }
            catch (SQLException e) {
                throw new RuntimeException(e);
            }
            finally {
                connPool.releaseConnection(conn);
            }

            return System.currentTimeMillis() - start;
        }
    }

    private static class SimpleConnectionPool implements AutoCloseable {
        private final List<Connection> connPool;
        private final List<Connection> usedConnections;

        private SimpleConnectionPool(String url, int port, int size) throws SQLException {
            this.connPool = new ArrayList<>(size);
            this.usedConnections = new ArrayList<>(size); // "jdbc:ignite:thin://127.0.0.1:"

            //
            for (int i = 0; i < size; i++) {
                Connection conn = DriverManager.getConnection(url + port);

                conn.setSchema("PUBLIC");

                connPool.add(conn);
            }
        }

        public synchronized Connection getConnection() {
            Connection conn = connPool.remove(connPool.size() - 1);

            usedConnections.add(conn);

            return conn;
        }

        public synchronized boolean releaseConnection(Connection conn) {
            connPool.add(conn);

            return usedConnections.remove(conn);
        }

        private static Connection createConnection(
            String url, String user, String password)
            throws SQLException {
            return DriverManager.getConnection(url, user, password);
        }

        @Override public synchronized void close() throws Exception {
            connPool.forEach(U::closeQuiet);
            usedConnections.forEach(U::closeQuiet);
        }
    }

    private static class BlockingExecutor {
        final Semaphore semaphore;
        final ExecutorService executor;

        BlockingExecutor(final int taskLimit) {
            semaphore = new Semaphore(taskLimit);
            executor = new ThreadPoolExecutor(
                taskLimit,
                taskLimit,
                10,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(taskLimit));
        }

        public void execute(final Runnable r) {
            try {
                semaphore.acquire();
            }
            catch (InterruptedException e) {
                e.printStackTrace();
                return;
            }

            final Runnable semaphoredRunnable = () -> {
                try {
                    r.run();
                }
                finally {
                    semaphore.release();
                }
            };

            executor.execute(semaphoredRunnable);
        }

        public void stopAndWaitForTermination() throws InterruptedException {
            executor.shutdown();
            executor.awaitTermination(TEST_TIMEOUT, TimeUnit.MILLISECONDS);
        }
    }

    private static class PredefinedQueriesSupplier implements Supplier<String> {
        private final Collection<String> qrys;
        private Iterator<String> it;

        private PredefinedQueriesSupplier(Collection<String> qrys) {
            assert !qrys.isEmpty();
            this.qrys = qrys;
            this.it = qrys.iterator();
        }

        @Override public synchronized String get() {
            if (!it.hasNext())
                it = qrys.iterator();

            return it.next();
        }
    }
}
