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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.UUID;
import org.apache.ignite.internal.client.GridClient;
import org.apache.ignite.internal.client.GridClientAuthenticationException;
import org.apache.ignite.internal.client.GridClientClosedException;
import org.apache.ignite.internal.client.GridClientClusterState;
import org.apache.ignite.internal.client.GridClientCompute;
import org.apache.ignite.internal.client.GridClientConfiguration;
import org.apache.ignite.internal.client.GridClientDisconnectedException;
import org.apache.ignite.internal.client.GridClientException;
import org.apache.ignite.internal.client.GridClientFactory;
import org.apache.ignite.internal.client.GridClientHandshakeException;
import org.apache.ignite.internal.client.GridClientNode;
import org.apache.ignite.internal.client.GridServerUnreachableException;
import org.apache.ignite.internal.client.impl.connection.GridClientConnectionResetException;
import org.apache.ignite.internal.commandline.cache.CacheArguments;
import org.apache.ignite.internal.commandline.cache.CacheCommand;
import org.apache.ignite.internal.processors.cache.verify.CacheInfo;
import org.apache.ignite.internal.processors.cache.verify.ContentionInfo;
import org.apache.ignite.internal.processors.cache.verify.PartitionEntryHashRecord;
import org.apache.ignite.internal.processors.cache.verify.PartitionHashRecord;
import org.apache.ignite.internal.processors.cache.verify.PartitionKey;
import org.apache.ignite.internal.util.typedef.F;
import org.apache.ignite.internal.util.typedef.X;
import org.apache.ignite.internal.visor.VisorTaskArgument;
import org.apache.ignite.internal.visor.baseline.VisorBaselineNode;
import org.apache.ignite.internal.visor.baseline.VisorBaselineOperation;
import org.apache.ignite.internal.visor.baseline.VisorBaselineTask;
import org.apache.ignite.internal.visor.baseline.VisorBaselineTaskArg;
import org.apache.ignite.internal.visor.baseline.VisorBaselineTaskResult;
import org.apache.ignite.internal.visor.verify.ValidateIndexesPartitionResult;
import org.apache.ignite.internal.visor.verify.VisorContentionTask;
import org.apache.ignite.internal.visor.verify.VisorContentionTaskArg;
import org.apache.ignite.internal.visor.verify.VisorContentionTaskResult;
import org.apache.ignite.internal.visor.verify.VisorIdleAnalyzeTask;
import org.apache.ignite.internal.visor.verify.VisorIdleAnalyzeTaskArg;
import org.apache.ignite.internal.visor.verify.VisorIdleAnalyzeTaskResult;
import org.apache.ignite.internal.visor.verify.VisorIdleVerifyTask;
import org.apache.ignite.internal.visor.verify.VisorIdleVerifyTaskArg;
import org.apache.ignite.internal.visor.verify.VisorIdleVerifyTaskResult;
import org.apache.ignite.internal.visor.verify.VisorValidateIndexesJobResult;
import org.apache.ignite.internal.visor.verify.VisorValidateIndexesTaskArg;
import org.apache.ignite.internal.visor.verify.VisorValidateIndexesTaskResult;
import org.apache.ignite.internal.visor.verify.VisorViewCacheTaskArg;
import org.apache.ignite.internal.visor.verify.VisorViewCacheTaskResult;
import org.apache.ignite.plugin.security.SecurityCredentials;
import org.apache.ignite.plugin.security.SecurityCredentialsBasicProvider;
import org.jetbrains.annotations.NotNull;

import static org.apache.ignite.internal.IgniteVersionUtils.ACK_VER_STR;
import static org.apache.ignite.internal.IgniteVersionUtils.COPYRIGHT;
import static org.apache.ignite.internal.commandline.Command.ACTIVATE;
import static org.apache.ignite.internal.commandline.Command.BASELINE;
import static org.apache.ignite.internal.commandline.Command.CACHE;
import static org.apache.ignite.internal.commandline.Command.DEACTIVATE;
import static org.apache.ignite.internal.commandline.Command.STATE;
import static org.apache.ignite.internal.visor.baseline.VisorBaselineOperation.ADD;
import static org.apache.ignite.internal.visor.baseline.VisorBaselineOperation.COLLECT;
import static org.apache.ignite.internal.visor.baseline.VisorBaselineOperation.REMOVE;
import static org.apache.ignite.internal.visor.baseline.VisorBaselineOperation.SET;
import static org.apache.ignite.internal.visor.baseline.VisorBaselineOperation.VERSION;

/**
 * Class that execute several commands passed via command line.
 */
public class CommandHandler {
    /** */
    static final String DFLT_HOST = "127.0.0.1";

    /** */
    static final String DFLT_PORT = "11211";

    /** */
    private static final String CMD_HELP = "--help";

    /** */
    private static final String CMD_HOST = "--host";

    /** */
    private static final String CMD_PORT = "--port";

    /** */
    private static final String CMD_PASSWORD = "--password";

    /** */
    private static final String CMD_USER = "--user";

    /** Force option is used for auto confirmation. */
    private static final String CMD_FORCE = "--force";

    /** List of optional auxiliary commands. */
    private static final Set<String> AUX_COMMANDS = new HashSet<>();
    static {
        AUX_COMMANDS.add(CMD_HELP);
        AUX_COMMANDS.add(CMD_HOST);
        AUX_COMMANDS.add(CMD_PORT);
        AUX_COMMANDS.add(CMD_PASSWORD);
        AUX_COMMANDS.add(CMD_USER);
        AUX_COMMANDS.add(CMD_FORCE);
    }

    /** */
    private static final String BASELINE_ADD = "add";

    /** */
    private static final String BASELINE_REMOVE = "remove";

    /** */
    private static final String BASELINE_COLLECT = "collect";

    /** */
    private static final String BASELINE_SET = "set";

    /** */
    private static final String BASELINE_SET_VERSION = "version";

    /** */
    private static final String DELIM = "--------------------------------------------------------------------------------";

    /** Validate indexes task name. */
    private static final String VALIDATE_INDEXES_TASK = "org.apache.ignite.internal.visor.verify.VisorValidateIndexesTask";

    /** */
    public static final int EXIT_CODE_OK = 0;

    /** */
    public static final int EXIT_CODE_INVALID_ARGUMENTS = 1;

    /** */
    public static final int EXIT_CODE_CONNECTION_FAILED = 2;

    /** */
    public static final int ERR_AUTHENTICATION_FAILED = 3;

    /** */
    public static final int EXIT_CODE_UNEXPECTED_ERROR = 4;

    /** */
    private static final Scanner IN = new Scanner(System.in);

    /** */
    private Iterator<String> argsIt;

    /** */
    private String peekedArg;

    /**
     * Output specified string to console.
     *
     * @param s String to output.
     */
    private void log(String s) {
        System.out.println(s);
    }

    /**
     * Provides a prompt, then reads a single line of text from the console.
     *
     * @param prompt text
     * @return A string containing the line read from the console
     */
    private String readLine(String prompt) {
        System.out.print(prompt);

        return IN.nextLine();
    }

    /**
     * Output empty line.
     */
    private void nl() {
        System.out.println("");
    }

    /**
     * Print error to console.
     *
     * @param errCode Error code to return.
     * @param s Optional message.
     * @param e Error to print.
     */
    private int error(int errCode, String s, Throwable e) {
        if (!F.isEmpty(s))
            log(s);

        String msg = e.getMessage();

        if (F.isEmpty(msg))
            msg = e.getClass().getName();

        if (msg.startsWith("Failed to handle request")) {
            int p = msg.indexOf("err=");

            msg = msg.substring(p + 4, msg.length() - 1);
        }

        log("Error: " + msg);

        return errCode;
    }

    /**
     * Requests interactive user confirmation if forthcoming operation is dangerous.
     *
     * @param args Arguments.
     * @return {@code true} if operation confirmed (or not needed), {@code false} otherwise.
     */
    private boolean confirm(Arguments args) {
        String prompt = confirmationPrompt(args);

        if (prompt == null)
            return true;

        return "y".equalsIgnoreCase(readLine(prompt));
    }

    /**
     * @param args Arguments.
     * @return Prompt text if confirmation needed, otherwise {@code null}.
     */
    private String confirmationPrompt(Arguments args) {
        if (args.force())
            return null;

        String str = null;

        switch (args.command()) {
            case DEACTIVATE:
                str = "Warning: the command will deactivate a cluster.";
                break;

            case BASELINE:
                if (!BASELINE_COLLECT.equals(args.baselineAction()))
                    str = "Warning: the command will perform changes in baseline.";

                break;

            case CACHE:
                if (args.cacheArgs().command() == CacheCommand.DESTROY) {
                    str = "Warning: the command will destroy all caches that match " +
                        args.cacheArgs().regex() + " pattern.";
                }

                break;

            default:
                break;
        }

        return str == null ? null : str + "\nPress 'y' to continue...";
    }

    /**
     * @param rawArgs Arguments.
     */
    private void initArgIterator(List<String> rawArgs) {
        argsIt = rawArgs.iterator();
        peekedArg = null;
    }

    /**
     * @return Returns {@code true} if the iteration has more elements.
     */
    private boolean hasNextArg() {
        return peekedArg != null || argsIt.hasNext();
    }

    /**
     * Activate cluster.
     *
     * @param client Client.
     * @throws GridClientException If failed to activate.
     */
    private void activate(GridClient client) throws Throwable {
        try {
            GridClientClusterState state = client.state();

            state.active(true);

            log("Cluster activated");
        }
        catch (Throwable e) {
            log("Failed to activate cluster.");

            throw e;
        }
    }

    /**
     * Deactivate cluster.
     *
     * @param client Client.
     * @throws Throwable If failed to deactivate.
     */
    private void deactivate(GridClient client) throws Throwable {
        try {
            GridClientClusterState state = client.state();

            state.active(false);

            log("Cluster deactivated");
        }
        catch (Throwable e) {
            log("Failed to deactivate cluster.");

            throw e;
        }
    }

    /**
     * Print cluster state.
     *
     * @param client Client.
     * @throws Throwable If failed to print state.
     */
    private void state(GridClient client) throws Throwable {
        try {
            GridClientClusterState state = client.state();

            log("Cluster is " + (state.active() ? "active" : "inactive"));
        }
        catch (Throwable e) {
            log("Failed to get cluster state.");

            throw e;
        }
    }

    /**
     * @param client Client
     * @param taskCls Task class.
     * @param taskArgs Task args.
     * @return Task result.
     * @throws GridClientException If failed to execute task.
     */
    private <R> R executeTask(GridClient client, Class<?> taskCls, Object taskArgs) throws GridClientException {
        return executeTaskByNameOnNode(client, taskCls.getName(), taskArgs, null);
    }

    /**
     * @param client Client
     * @param taskClsName Task class name.
     * @param taskArgs Task args.
     * @param nodeId Node ID to execute task at (if null, random node will be chosen by balancer).
     * @return Task result.
     * @throws GridClientException If failed to execute task.
     */
    private <R> R executeTaskByNameOnNode(GridClient client, String taskClsName, Object taskArgs, UUID nodeId
    ) throws GridClientException {
        GridClientCompute compute = client.compute();

        GridClientNode node = null;

        if (nodeId == null) {
            List<GridClientNode> nodes = new ArrayList<>();

            for (GridClientNode n : compute.nodes()) {
                if (n.connectable())
                    nodes.add(n);
            }

            if (F.isEmpty(nodes))
                throw new GridClientDisconnectedException("Connectable node not found", null);

            node = compute.balancer().balancedNode(nodes);
        }
        else {
            for (GridClientNode n : compute.nodes()) {
                if (n.connectable() && nodeId.equals(n.nodeId())) {
                    node = n;

                    break;
                }
            }

            if (node == null)
                throw new IllegalArgumentException("Node with id=" + nodeId + " not found");
        }

        return compute.projection(node).execute(taskClsName, new VisorTaskArgument<>(node.nodeId(), taskArgs, false));
    }

    /**
     * Executes --cache subcommand.
     *
     * @param client Client.
     * @param cacheArgs Cache args.
     */
    private void cache(GridClient client, CacheArguments cacheArgs) throws Throwable {
        switch (cacheArgs.command()) {
            case HELP:


                // Print help.
                break;

            case IDLE_VERIFY:
                cacheIdleVerify(client, cacheArgs);

                break;

            case IDLE_ANALYZE:
                cacheIdleAnalyze(client, cacheArgs);

                break;

            case SEQ:
            case UPDATE_SEQ:
            case DESTROY_SEQ:
            case GROUPS:
            case AFFINITY:
            case DESTROY: {
                cacheView(client, cacheArgs);

                break;
            }

            case VALIDATE_INDEXES: {
                cacheValidateIndexes(client, cacheArgs);

                break;
            }

            case CONT: {
                cacheContention(client, cacheArgs);

                break;
            }

            default:
                throw new IllegalArgumentException("Unexpected cache command: " + cacheArgs.command());
        }
    }

    /**
     * @param client Client.
     * @param cacheArgs Cache args.
     */
    private void cacheContention(GridClient client, CacheArguments cacheArgs) throws GridClientException {
        VisorContentionTaskArg taskArg = new VisorContentionTaskArg(
            cacheArgs.minQueueSize(), cacheArgs.maxPrint());

        VisorContentionTaskResult res = executeTaskByNameOnNode(
            client, VisorContentionTask.class.getName(), taskArg, cacheArgs.nodeId());

        if (!F.isEmpty(res.exceptions())) {
            log("Contention check failed on nodes:");

            for (Map.Entry<UUID, Exception> e : res.exceptions().entrySet()) {
                log("Node ID = " + e.getKey());

                log("Exception message:");
                log(e.getValue().getMessage());
                nl();
            }
        }

        for (ContentionInfo info : res.getInfos())
            info.print();
    }

    /**
     * @param client Client.
     * @param cacheArgs Cache args.
     */
    private void cacheValidateIndexes(GridClient client, CacheArguments cacheArgs) throws GridClientException {
        VisorValidateIndexesTaskArg taskArg = new VisorValidateIndexesTaskArg(cacheArgs.caches());

        VisorValidateIndexesTaskResult taskRes = executeTaskByNameOnNode(
            client, VALIDATE_INDEXES_TASK, taskArg, cacheArgs.nodeId());

        if (!F.isEmpty(taskRes.exceptions())) {
            log("Index validation failed on nodes:");

            for (Map.Entry<UUID, Exception> e : taskRes.exceptions().entrySet()) {
                log("Node ID = " + e.getKey());

                log("Exception message:");
                log(e.getValue().getMessage());
                nl();
            }
        }

        boolean errors = false;

        for (Map.Entry<UUID, VisorValidateIndexesJobResult> nodeEntry : taskRes.results().entrySet()) {
            Map<PartitionKey, ValidateIndexesPartitionResult> map = nodeEntry.getValue().response();

            for (Map.Entry<PartitionKey, ValidateIndexesPartitionResult> e : map.entrySet()) {
                ValidateIndexesPartitionResult res = e.getValue();

                if (!res.issues().isEmpty()) {
                    errors = true;

                    log(e.getKey().toString() + " " + e.getValue().toString());

                    for (ValidateIndexesPartitionResult.Issue is : res.issues())
                        log(is.toString());
                }
            }
        }

        if (!errors)
            log("validate_indexes has finished, no issues found.");
        else
            log("validate_indexes has finished with errors (listed above).");
    }

    /**
     * @param client Client.
     * @param cacheArgs Cache args.
     */
    private void cacheView(GridClient client, CacheArguments cacheArgs) throws GridClientException {
        VisorViewCacheTaskArg taskArg = new VisorViewCacheTaskArg(
            cacheArgs.regex(), cacheArgs.command(), cacheArgs.newUpdateSequenceValue());

        VisorViewCacheTaskResult res = executeTaskByNameOnNode(
            client, VisorViewCacheTaskResult.class.getName(), taskArg, cacheArgs.nodeId());

        for (CacheInfo info : res.cacheInfos())
            info.print();

        if (cacheArgs.command() == CacheCommand.DESTROY)
            log("Destroyed caches count: " + res.cacheInfos().size());
    }

    /**
     * @param client Client.
     * @param cacheArgs Cache args.
     */
    private void cacheIdleAnalyze(GridClient client, CacheArguments cacheArgs) throws GridClientException {
        VisorIdleAnalyzeTaskResult res = executeTask(client, VisorIdleAnalyzeTask.class,
            new VisorIdleAnalyzeTaskArg(new PartitionKey(cacheArgs.groupId(), cacheArgs.partitionId(), null)));

        Map<PartitionHashRecord, List<PartitionEntryHashRecord>> div = res.getDivergedEntries();

        if (div.isEmpty()) {
            log("idle_analyze check has finished, no coflicts have been found in partition.");
            nl();
        }
        else {
            log("idle_analyze check has finished, found " + div.size() + " conflict keys.");
            nl();

            for (Map.Entry<PartitionHashRecord, List<PartitionEntryHashRecord>> e : div.entrySet()) {
                log("Differences at node " + e.getKey().consistentId() + ":");

                for (PartitionEntryHashRecord rec : e.getValue())
                    log(rec.toString());

                nl();
            }
        }
    }

    /**
     * @param client Client.
     * @param cacheArgs Cache args.
     */
    private void cacheIdleVerify(GridClient client, CacheArguments cacheArgs) throws GridClientException {
        VisorIdleVerifyTaskResult res = executeTask(
            client, VisorIdleVerifyTask.class, new VisorIdleVerifyTaskArg(cacheArgs.caches()));

        Map<PartitionKey, List<PartitionHashRecord>> conflicts = res.getConflicts();

        if (conflicts.isEmpty()) {
            log("idle_verify check has finished, no conflicts have been found.");
            nl();
        }
        else {
            log ("idle_verify check has finished, found " + conflicts.size() + " conflict partitions.");
            nl();

            for (Map.Entry<PartitionKey, List<PartitionHashRecord>> entry : conflicts.entrySet()) {
                log("Conflict partition: " + entry.getKey());
                log("Partition instances: " + entry.getValue());
            }
        }
    }

    /**
     * Change baseline.
     *
     * @param client Client.
     * @param baselineAct Baseline action to execute.  @throws GridClientException If failed to execute baseline action.
     * @param baselineArgs Baseline action arguments.
     * @throws Throwable If failed to execute baseline action.
     */
    private void baseline(GridClient client, String baselineAct, String baselineArgs) throws Throwable {
        switch (baselineAct) {
            case BASELINE_ADD:
                baselineAdd(client, baselineArgs);
                break;

            case BASELINE_REMOVE:
                baselineRemove(client, baselineArgs);
                break;

            case BASELINE_SET:
                baselineSet(client, baselineArgs);
                break;

            case BASELINE_SET_VERSION:
                baselineVersion(client, baselineArgs);
                break;

            case BASELINE_COLLECT:
                baselinePrint(client);
                break;
        }
    }

    /**
     * Prepare task argument.
     *
     * @param op Operation.
     * @param s Argument from command line.
     * @return Task argument.
     */
    private VisorBaselineTaskArg arg(VisorBaselineOperation op, String s) {
        switch (op) {
            case ADD:
            case REMOVE:
            case SET:
                if(F.isEmpty(s))
                    throw new IllegalArgumentException("Empty list of consistent IDs");

                List<String> consistentIds = new ArrayList<>();

                for (String consistentId : s.split(","))
                    consistentIds.add(consistentId.trim());

                return new VisorBaselineTaskArg(op, -1, consistentIds);

            case VERSION:
                try {
                    long topVer = Long.parseLong(s);

                    return new VisorBaselineTaskArg(op, topVer, null);
                }
                catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid topology version: " + s, e);
                }

            default:
                return new VisorBaselineTaskArg(op, -1, null);
        }
    }

    /**
     * Print baseline topology.
     *
     * @param res Task result with baseline topology.
     */
    private void baselinePrint0(VisorBaselineTaskResult res) {
        log("Cluster state: " + (res.isActive() ? "active" : "inactive"));
        log("Current topology version: " + res.getTopologyVersion());
        nl();

        Map<String, VisorBaselineNode> baseline = res.getBaseline();
        Map<String, VisorBaselineNode> servers = res.getServers();

        if (F.isEmpty(baseline))
            log("Baseline nodes not found.");
        else {
            log("Baseline nodes:");

            for(VisorBaselineNode node : baseline.values()) {
                log("    ConsistentID=" + node.getConsistentId() + ", STATE=" +
                    (servers.containsKey(node.getConsistentId()) ? "ONLINE" : "OFFLINE"));
            }

            log(DELIM);
            log("Number of baseline nodes: " + baseline.size());

            nl();

            List<VisorBaselineNode> others = new ArrayList<>();

            for (VisorBaselineNode node : servers.values()) {
                if (!baseline.containsKey(node.getConsistentId()))
                    others.add(node);
            }

            if (F.isEmpty(others))
                log("Other nodes not found.");
            else {
                log("Other nodes:");

                for(VisorBaselineNode node : others)
                    log("    ConsistentID=" + node.getConsistentId());

                log("Number of other nodes: " + others.size());
            }
        }
    }

    /**
     * Print current baseline.
     *
     * @param client Client.
     */
    private void baselinePrint(GridClient client) throws GridClientException {
        VisorBaselineTaskResult res = executeTask(client, VisorBaselineTask.class, arg(COLLECT, ""));

        baselinePrint0(res);
    }

    /**
     * Add nodes to baseline.
     *
     * @param client Client.
     * @param baselineArgs Baseline action arguments.
     * @throws Throwable If failed to add nodes to baseline.
     */
    private void baselineAdd(GridClient client, String baselineArgs) throws Throwable {
        try {
            VisorBaselineTaskResult res = executeTask(client, VisorBaselineTask.class, arg(ADD, baselineArgs));

            baselinePrint0(res);
        }
        catch (Throwable e) {
            log("Failed to add nodes to baseline.");

            throw e;
        }
    }

    /**
     * Remove nodes from baseline.
     *
     * @param client Client.
     * @param consistentIds Consistent IDs.
     * @throws Throwable If failed to remove nodes from baseline.
     */
    private void baselineRemove(GridClient client, String consistentIds) throws Throwable {
        try {
            VisorBaselineTaskResult res = executeTask(client, VisorBaselineTask.class, arg(REMOVE, consistentIds));

            baselinePrint0(res);
        }
        catch (Throwable e) {
            log("Failed to remove nodes from baseline.");

            throw e;
        }
    }

    /**
     * Set baseline.
     *
     * @param client Client.
     * @param consistentIds Consistent IDs.
     * @throws Throwable If failed to set baseline.
     */
    private void baselineSet(GridClient client, String consistentIds) throws Throwable {
        try {
            VisorBaselineTaskResult res = executeTask(client, VisorBaselineTask.class, arg(SET, consistentIds));

            baselinePrint0(res);
        }
        catch (Throwable e) {
            log("Failed to set baseline.");

            throw e;
        }
    }

    /**
     * Set baseline by topology version.
     *
     * @param client Client.
     * @param arg Argument from command line.
     */
    private void baselineVersion(GridClient client, String arg) throws GridClientException {
        try {
            VisorBaselineTaskResult res = executeTask(client, VisorBaselineTask.class, arg(VERSION, arg));

            baselinePrint0(res);
        }
        catch (Throwable e) {
            log("Failed to set baseline with specified topology version.");

            throw e;
        }
    }

    /**
     * @param e Exception to check.
     * @return {@code true} if specified exception is {@link GridClientAuthenticationException}.
     */
    private boolean isAuthError(Throwable e) {
        return X.hasCause(e, GridClientAuthenticationException.class);
    }

    /**
     * @param e Exception to check.
     * @return {@code true} if specified exception is a connection error.
     */
    private boolean isConnectionError(Throwable e) {
        return e instanceof GridClientClosedException ||
            e instanceof GridClientConnectionResetException ||
            e instanceof GridClientDisconnectedException ||
            e instanceof GridClientHandshakeException ||
            e instanceof GridServerUnreachableException;
    }

    /**
     * Print command usage.
     *
     * @param desc Command description.
     * @param args Arguments.
     */
    private void usage(String desc, Command cmd, String... args) {
        log(desc);
        log("    control.sh [--host HOST_OR_IP] [--port PORT] [--user USER] [--password PASSWORD] " + cmd.text() + String.join("", args));
        nl();
    }

    /**
     * Extract next argument.
     *
     * @param err Error message.
     * @return Next argument value.
     */
    private String nextArg(String err) {
        if (peekedArg != null) {
            String res = peekedArg;

            peekedArg = null;

            return res;
        }

        if (argsIt.hasNext())
            return argsIt.next();

        throw new IllegalArgumentException(err);
    }

    /**
     * Returns the next argument in the iteration, without advancing the iteration.
     *
     * @return Next argument value or {@code null} if no next argument.
     */
    private String peekNextArg() {
        if (peekedArg == null && argsIt.hasNext())
            peekedArg = argsIt.next();

        return peekedArg;
    }

    /**
     * Parses and validates arguments.
     *
     * @param rawArgs Array of arguments.
     * @return Arguments bean.
     * @throws IllegalArgumentException In case arguments aren't valid.
     */
    @NotNull Arguments parseAndValidate(List<String> rawArgs) {
        String host = DFLT_HOST;

        String port = DFLT_PORT;

        String user = null;

        String pwd = null;

        String baselineAct = "";

        String baselineArgs = "";

        boolean force = false;

        CacheArguments cacheArgs = null;

        List<Command> commands = new ArrayList<>();

        initArgIterator(rawArgs);

        while (hasNextArg()) {
            String str = nextArg("").toLowerCase();

            Command cmd = Command.of(str);

            if (cmd != null) {
                switch (cmd) {
                    case ACTIVATE:
                    case DEACTIVATE:
                    case STATE:
                        commands.add(Command.of(str));
                        break;

                    case BASELINE:
                        commands.add(BASELINE);

                        baselineAct = BASELINE_COLLECT; //default baseline action

                        str = peekNextArg();

                        if (str != null) {
                            str = str.toLowerCase();

                            if (BASELINE_ADD.equals(str) || BASELINE_REMOVE.equals(str) ||
                                BASELINE_SET.equals(str) || BASELINE_SET_VERSION.equals(str)) {
                                baselineAct = nextArg("Expected baseline action");

                                baselineArgs = nextArg("Expected baseline arguments");
                            }
                        }

                        break;

                    case CACHE:
                        commands.add(CACHE);

                        cacheArgs = parseAndValidateCacheArgs();

                        break;
                }
            }
            else {
                switch (str) {
                    case CMD_HOST:
                        host = nextArg("Expected host name");

                        break;

                    case CMD_PORT:
                        port = nextArg("Expected port number");

                        try {
                            int p = Integer.parseInt(port);

                            if (p <= 0 || p > 65535)
                                throw new IllegalArgumentException("Invalid value for port: " + port);
                        }
                        catch (NumberFormatException ignored) {
                            throw new IllegalArgumentException("Invalid value for port: " + port);
                        }

                        break;

                    case CMD_USER:
                        user = nextArg("Expected user name");

                        break;

                    case CMD_PASSWORD:
                        pwd = nextArg("Expected password");

                        break;

                    case CMD_FORCE:
                        force = true;

                        break;

                    default:
                        throw new IllegalArgumentException("Unexpected argument: " + str);
                }
            }
        }

        int sz = commands.size();

        if (sz < 1)
            throw new IllegalArgumentException("No action was specified");

        if (sz > 1)
            throw new IllegalArgumentException("Only one action can be specified, but found: " + sz);

        Command cmd = commands.get(0);

        boolean hasUsr = F.isEmpty(user);
        boolean hasPwd = F.isEmpty(pwd);

        if (hasUsr != hasPwd)
            throw new IllegalArgumentException("Both user and password should be specified");

        return new Arguments(cmd, host, port, user, pwd, baselineAct, baselineArgs, force, cacheArgs);
    }

    /**
     * Parses and validates cache arguments.
     *
     * @return --cache subcommand arguments in case validation is successful.
     */
    private CacheArguments parseAndValidateCacheArgs() {
        if (!hasNextCacheArg()) {
            throw new IllegalArgumentException("Arguments are expected for --cache subcommand, " +
                "run --cache help for more info.");
        }

        CacheArguments cacheArgs = new CacheArguments();

        String str = nextArg("").toLowerCase();

        CacheCommand cmd = CacheCommand.of(str);

        if (cmd == null)
            throw new IllegalArgumentException("Unxepected --cache command: " + cmd);

        cacheArgs.command(cmd);

        switch (cmd) {
            case HELP:
                log("--cache subcommand allows to do the following operations:");

                usage("  Verify partition counters and hashes between primary and backups on idle cluster:", CACHE, " idle_verify [cache1,...,cacheN]");
                usage("  If idle_verify found conflicts, find exact keys that differ between primary and backups on idle cluster:", CACHE, " idle_analyze groupId partitionId");
                usage("  Show existing atomic sequences that match regex:", CACHE, " seq regexPattern [nodeId]");
                usage("  Update atomic sequences that match regex:", CACHE, " update_seq regexPattern newValue");
                usage("  Destroy atomic sequences that match regex:", CACHE, " destroy_seq regexPattern [nodeId]");
                usage("  Show cache groups info for caches that match regex:", CACHE, " groups regexPattern [nodeId]");
                usage("  Show affinity distribution info for caches that match regex:", CACHE, " affinity regexPattern [nodeId]");
                usage("  Destroy caches that match regex:", CACHE, " destroy regexPattern [nodeId] [--force]");
                usage("  Validate custom indexes on idle cluster:", CACHE, " validate_indexes [cache1,...,cacheN] [nodeId]");
                usage("  Show hot keys that are point of contention for multiple transactions:", CACHE, " cont minQueueSize [nodeId] [maxPrint]");

                break;

            case IDLE_VERIFY:
                parseCacheNamesIfPresent(cacheArgs);

                break;

            case IDLE_ANALYZE:
                cacheArgs.groupId(Integer.valueOf(nextArg("Expected cache or group ID")));
                cacheArgs.partitionId(Integer.valueOf(nextArg("Expected partition ID")));

                break;

            case SEQ:
            case UPDATE_SEQ:
            case DESTROY_SEQ:
            case GROUPS:
            case AFFINITY:
            case DESTROY:
                cacheArgs.regex(nextArg("Regex is expected"));

                if (cmd == CacheCommand.UPDATE_SEQ) {
                    Long seqVal = Long.parseLong(nextArg("New sequence value expected"));

                    cacheArgs.newUpdateSequenceValue(String.valueOf(seqVal));
                }
                else {
                    if (hasNextCacheArg())
                        cacheArgs.nodeId(UUID.fromString(nextArg("")));
                }

                break;

            case CONT:
                cacheArgs.minQueueSize(Integer.parseInt(nextArg("Min queue size expected")));

                if (hasNextCacheArg())
                    cacheArgs.nodeId(UUID.fromString(nextArg("")));

                if (hasNextCacheArg())
                    cacheArgs.maxPrint(Integer.parseInt(nextArg("")));
                else
                    cacheArgs.maxPrint(10);

                break;

            case VALIDATE_INDEXES:
                parseCacheNamesIfPresent(cacheArgs);

                if (hasNextCacheArg())
                    cacheArgs.nodeId(UUID.fromString(nextArg("")));

                break;
        }

        if (hasNextCacheArg())
            throw new IllegalArgumentException("Unexpected argument of --cache subcommand: " + peekNextArg());

        return cacheArgs;
    }

    /**
     * @return <code>true</code> if there's next argument for --cache subcommand.
     */
    private boolean hasNextCacheArg() {
        return hasNextArg() && Command.of(peekNextArg()) == null && !AUX_COMMANDS.contains(peekNextArg());
    }

    /**
     * @param cacheArgs Cache args.
     */
    private void parseCacheNamesIfPresent(CacheArguments cacheArgs) {
        if (hasNextCacheArg()) {
            String cacheNames = nextArg("");

            String[] cacheNamesArr = cacheNames.split(",");
            Set<String> cacheNamesSet = new HashSet<>();

            for (String cacheName : cacheNamesArr) {
                if (F.isEmpty(cacheName))
                    throw new IllegalArgumentException("Non-empty cache names expected.");

                cacheNamesSet.add(cacheName.trim());
            }

            cacheArgs.caches(cacheNamesSet);
        }
    }

    /**
     * Parse and execute command.
     *
     * @param rawArgs Arguments to parse and execute.
     * @return Exit code.
     */
    public int execute(List<String> rawArgs) {
        log("Control utility [ver. " + ACK_VER_STR + "]");
        log(COPYRIGHT);
        log("User: " + System.getProperty("user.name"));
        log(DELIM);

        try {
            if (F.isEmpty(rawArgs) || (rawArgs.size() == 1 && CMD_HELP.equalsIgnoreCase(rawArgs.get(0)))) {
                log("This utility can do the following commands:");

                usage("  Activate cluster:", ACTIVATE);
                usage("  Deactivate cluster:", DEACTIVATE, " [--force]");
                usage("  Print current cluster state:", STATE);
                usage("  Print cluster baseline topology:", BASELINE);
                usage("  Add nodes into baseline topology:", BASELINE, " add consistentId1[,consistentId2,....,consistentIdN] [--force]");
                usage("  Remove nodes from baseline topology:", BASELINE, " remove consistentId1[,consistentId2,....,consistentIdN] [--force]");
                usage("  Set baseline topology:", BASELINE, " set consistentId1[,consistentId2,....,consistentIdN] [--force]");
                usage("  Set baseline topology based on version:", BASELINE, " version topologyVersion [--force]");

                log("The utility has --cache subcommand to view and control state of caches in cluster.");
                log("  More info:    control.sh --cache help");
                nl();

                log("By default cluster deactivation and changes in baseline topology commands request interactive confirmation. ");
                log("  --force option can be used to execute commands without prompting for confirmation.");
                nl();

                log("Default values:");
                log("    HOST_OR_IP=" + DFLT_HOST);
                log("    PORT=" + DFLT_PORT);
                nl();

                log("Exit codes:");
                log("    " + EXIT_CODE_OK + " - successful execution.");
                log("    " + EXIT_CODE_INVALID_ARGUMENTS + " - invalid arguments.");
                log("    " + EXIT_CODE_CONNECTION_FAILED + " - connection failed.");
                log("    " + ERR_AUTHENTICATION_FAILED + " - authentication failed.");
                log("    " + EXIT_CODE_UNEXPECTED_ERROR + " - unexpected error.");

                return EXIT_CODE_OK;
            }

            Arguments args = parseAndValidate(rawArgs);

            if (!confirm(args)) {
                log("Operation canceled.");

                return EXIT_CODE_OK;
            }

            GridClientConfiguration cfg = new GridClientConfiguration();

            cfg.setServers(Collections.singletonList(args.host() + ":" + args.port()));

            if (!F.isEmpty(args.user())) {
                cfg.setSecurityCredentialsProvider(
                    new SecurityCredentialsBasicProvider(new SecurityCredentials(args.user(), args.password())));
            }

            try (GridClient client = GridClientFactory.start(cfg)) {
                switch (args.command()) {
                    case ACTIVATE:
                        activate(client);

                        break;

                    case DEACTIVATE:
                        deactivate(client);

                        break;

                    case STATE:
                        state(client);

                        break;

                    case BASELINE:
                        baseline(client, args.baselineAction(), args.baselineArguments());

                        break;

                    case CACHE:
                        cache(client, args.cacheArgs());

                        break;
                }
            }

            return 0;
        }
        catch (IllegalArgumentException e) {
            return error(EXIT_CODE_INVALID_ARGUMENTS, "Check arguments.", e);
        }
        catch (Throwable e) {
            if (isAuthError(e))
                return error(ERR_AUTHENTICATION_FAILED, "Authentication error.", e);

            if (isConnectionError(e))
                return error(EXIT_CODE_CONNECTION_FAILED, "Connection to cluster failed.", e);

            return error(EXIT_CODE_UNEXPECTED_ERROR, "", e);
        }
    }

    /**
     * @param args Arguments to parse and apply.
     */
    public static void main(String[] args) {
        CommandHandler hnd = new CommandHandler();

        System.exit(hnd.execute(Arrays.asList(args)));
    }
}

