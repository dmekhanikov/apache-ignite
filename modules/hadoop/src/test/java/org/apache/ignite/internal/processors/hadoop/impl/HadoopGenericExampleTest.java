package org.apache.ignite.internal.processors.hadoop.impl;

import java.net.URI;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapreduce.MRConfig;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.apache.ignite.configuration.HadoopConfiguration;
import org.apache.ignite.internal.processors.hadoop.HadoopJobProperty;
import org.apache.ignite.testframework.junits.multijvm2.HadoopAbstract2Test;
import org.apache.ignite.testframework.junits.multijvm2.IgniteNodeProxy2;

/**
 * Runs Hadoop Quasi Monte Carlo Pi estimation example.
 */
public abstract class HadoopGenericExampleTest extends HadoopAbstract2Test {
    /**
     *
     */
    static class FrameworkParameters {
        int maps;
        int reduces = 1;
        String workDir;
        String user;

        public FrameworkParameters(int numMaps, int numReduces, String workDirBase, String user) {
            this.maps = numMaps;
            this.reduces = numReduces;
            this.workDir = workDirBase;
            this.user = user;
        }

        public int numMaps() {
            return maps;
        }

        public String getWorkDir(String exampleName) {
            return workDir + "/" + user + "/" + exampleName;
        }

        public String user() {
            return user;
        }
    }

    static abstract class GenericHadoopExample  {
        abstract String name();

        void prepare(Configuration conf, FrameworkParameters fp) throws Exception {}

        abstract String[] parameters(FrameworkParameters fp);

        abstract Tool tool();

        abstract void verify(String[] parameters);
    }

    /**
     * @return The example.
     */
    protected abstract GenericHadoopExample example();

    /**
     * Gets base directory.
     * Note that this directory will be completely deleted in the and of the test.
     *
     * @return The base directory.
     */
    protected String getFsBase() {
        return "file:///tmp/hadoop-test-" + getUser();
    }

    /**
     * Desired number of maps in TeraSort job.
     * @return The number of maps.
     */
    protected int numMaps() {
        // TODO: multiplier of 4 and higher (8, 16) causes failure.
        return gridCount() * 2;
    }

    /**
     * The user to run Hadoop job on behalf of.
     * @return The user to run Hadoop job on behalf of.
     */
    protected String getUser() {
        return System.getProperty("user.name");
    }

    /** {@inheritDoc} */
    @Override protected void afterTest() throws Exception {
        IgniteNodeProxy2.stopAll();

        // Delete files used:
        getFileSystem().delete(new Path(getFsBase()), true);
    }

    /**
     * @return The execution parameters.
     */
    protected FrameworkParameters frameworkParameters() {
        return new FrameworkParameters(numMaps(), gridCount(), getFsBase(), getUser());
    }

    /**
     * Does actual calculation through Ignite API
     *
     * @param gzip Whether to use GZIP.
     */
    protected final void testImpl(boolean gzip) throws Exception {
        final GenericHadoopExample ex = example();

        System.out.println(ex.name() + ": ===============================================================");

        final Configuration conf = new JobConf();

        conf.set("fs.defaultFS", getFsBase());

        log().info("Desired number of maps: " + numMaps());

        // Ignite specific job properties:
        conf.setBoolean(HadoopJobProperty.SHUFFLE_MAPPER_STRIPED_OUTPUT.propertyName(), true);
        conf.setInt(HadoopJobProperty.SHUFFLE_MSG_SIZE.propertyName(), 4096);

        if (gzip)
            conf.setBoolean(HadoopJobProperty.SHUFFLE_MSG_GZIP.propertyName(), true);

        // Set the Ignite framework and the address:
        conf.set(MRConfig.FRAMEWORK_NAME, "ignite");
        conf.set(MRConfig.MASTER_ADDRESS, "localhost:11211");

        // Start real calculation:
        FrameworkParameters fp = frameworkParameters();

        ex.prepare(conf, fp);

        String[] args = ex.parameters(fp);

        int res = ToolRunner.run(conf, ex.tool(), args);

        assertEquals(0, res);

        ex.verify(args);
    }

    /**
     * Gets the file system we work upon.
     * @return The file system.
     * @throws Exception
     */
    protected FileSystem getFileSystem() throws Exception{
        return FileSystem.get(new URI(getFsBase()), new Configuration());
    }

    /** {@inheritDoc} */
    @Override protected void beforeTest() throws Exception {
        FileSystem fs = getFileSystem();

        fs.delete(new Path(getFsBase()), true);
        fs.mkdirs(new Path(getFsBase()));

        super.beforeTest();

        startNodes();
    }

    /**
     * Runs the example test.l
     *
     * @throws Exception If failed.
     */
    public void testExample() throws Exception {
        testImpl(false);
    }

    /**
     * Creates Hadoop configuration for the test.
     * @return The {@link HadoopConfiguration}.
     */
    @Override protected HadoopConfiguration hadoopConfiguration(int idx, String name) {
        HadoopConfiguration hadoopCfg = super.hadoopConfiguration(idx, name);

        // See org.apache.ignite.configuration.HadoopConfiguration.DFLT_MAX_TASK_QUEUE_SIZE
        hadoopCfg.setMaxTaskQueueSize(30_000);

        return hadoopCfg;
    }
}
