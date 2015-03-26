/*
 * Copyright 2014 Midokura SARL
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.midonet.benchmarks;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.protobuf.CodedOutputStream;

import mpi.MPI;
import mpi.MPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observer;

import org.midonet.benchmarks.configuration.BenchmarkConfig;
import org.midonet.benchmarks.configuration.ConfigException;
import org.midonet.benchmarks.mpi.MPIBenchApp;
import org.midonet.benchmarks.storage.StorageServiceSupport;
import org.midonet.benchmarks.storage.StorageServiceSupport.StorageException;
import org.midonet.cluster.EntityIdSetEvent;
import org.midonet.cluster.data.Bridge;
import org.midonet.cluster.data.Port;
import org.midonet.cluster.data.Router;
import org.midonet.cluster.data.ports.BridgePort;

/**
 * This is the container of the code of the different benchmarking steps.
 * It is based on a generic storage service, so that different applications
 * can run the same code using different storage implementations (e.g.
 * DataClient or Zoom).
 */
public class TopologyBenchmark extends MPIBenchApp {

    protected StorageServiceSupport store;

    private static enum Comp {
        STEP_INITIALIZING("initializing"),
        STEP_BUILDLAYOUT("buildLayout"),
        STEP_TESTREAD("testRead"),
        STEP_TESTREADALLTOALL("testReadAllToAll"),
        STEP_TESTREADBRIDGESCHUNK("testReadBridgesChunk"),
        STEP_TESTREADPORTSCHUNK("testReadPortsChunk"),
        STEP_TESTUPDATEBRIDGES("testUpdateBridges"),
        STEP_TESTUPDATEBRIDGESALLTOALL("testUpdateBridgesAllToAll"),
        STEP_TESTUPDATEPORTS("testUpdatePorts"),
        STEP_TESTUPDATEPORTSALLTOALL("testUpdatePortsAllToAll"),
        STEP_TESTNOTIFYBRIDGE("testNotifyBridge"),
        STEP_TESTNOTIFYBRIDGECREATION("testNotifyBridgeCreation"),
        STEP_REMOVELAYOUT("removeLayout"),
        STEP_FINALIZING("finalizing"),

        LAYOUT("layout"),
        TENANT("tenant"),
        BRIDGE("bridge"),
        BRIDGEPORT("bridgeport");

        private final String name;
        Comp(String name) {
            this.name = name;
        }
    }

    private static enum Op {
        ELAPSED("elapsed"),
        CREATE("create"),
        READ("read"),
        READALL("readall"),
        READCHUNK("readchunk"),
        UPDATE("update"),
        UPDATEALL("updateall"),
        DELETE("delete"),
        NOTIFY("notify"),
        NOTIFYCREATION("notifycreation");

        private final String name;
        Op(String name) {
            this.name = name;
        }

    }

    private static enum Unit {
        TIME("ms"),
        RATIO("ratio");

        private final String name;
        Unit(String name) {
            this.name = name;
        }
    }

    private static final Logger log =
        LoggerFactory.getLogger(TopologyBenchmark.class);

    /**
     * Generate the value name for an experiment measure.
     * Please use the strings defined at the beginning of this class.
     *
     * @param comp is the component measured (tenant, bridge, whole layout...)
     * @param op is the operation being measured (read, ...)
     * @param unit indicates if the measure is a ratio or a time
     * @return the name of the value for the experiment data
     */
    private static String valueName(Comp comp, Op op, Unit unit) {
        return comp.name + "_" + op.name + "_" + unit.name;
    }

    /**
     * Get the system timestamp in millisecs
     */
    private static long getTimeStamp() {
        return (new Date()).getTime();
    }

    private BenchModels.Benchmark benchmarkProto;
    private List<BenchModels.ExpData> benchdata = new ArrayList<>();

    private final BenchmarkConfig cfg;
    private final PrintStream outStream;
    private CodedOutputStream binStream = null;

    // Provider router id
    private UUID providerRouterId;

    // Tenant router array
    private UUID[] allTenants;

    // Bridge array
    private UUID[] allBridges;

    // Port array
    private UUID[] allPorts;

    protected TopologyBenchmark(int mpiSize, int mpiRank, BenchmarkConfig cfg,
                                String benchname)
        throws MPIException, ConfigException {
        super(mpiSize, mpiRank, cfg);
        this.cfg = cfg;

        // configure output
        PrintStream output;
        if (cfg.getOutput().isEmpty()) {
            output = System.out;
        } else {
            try {
                output = new PrintStream(cfg.getOutput());
            } catch (FileNotFoundException e) {
                log.warn("failed to open output file {}",
                         cfg.getOutput(), e);
                output = System.out;
            }
        }
        outStream = output;
        FileOutputStream outbin = null;
        if (!cfg.getBinOutput().isEmpty()) {
            try {
                outbin = new FileOutputStream(cfg.getBinOutput());
                binStream = CodedOutputStream.newInstance(outbin);
            } catch (FileNotFoundException e) {
                log.warn("failed to open binary output file {}",
                         cfg.getBinOutput(), e);
            }
        }

        Set<String> zkNodes = new HashSet<>(
            Arrays.asList(cfg.getZkHosts().split(",")));

        BenchModels.EnvData.Zookeeper zkProto = BenchModels.EnvData.Zookeeper
            .newBuilder()
            .setZkInstances(zkNodes.size())
            .setZkHosts(cfg.getZkHosts())
            .build();

        BenchModels.EnvData envProto = BenchModels.EnvData
            .newBuilder()
            .setZkInfo(zkProto)
            .build();

        BenchModels.Topology topologyProto = BenchModels.Topology
            .newBuilder()
            .setNumTenants(cfg.getTopologyTenants())
            .setBridgesPerTenant(cfg.getTopologyBridgesPerTenant())
            .setPortsPerBridge(cfg.getTopologyPortsPerBridge())
            .build();

        benchmarkProto = BenchModels.Benchmark
            .newBuilder()
            .setName(benchname)
            .setTestbed(cfg.getTestbed())
            .setTstamp(getTimeStamp())
            .setHost(mpiHostName)
            .setEnv(envProto)
            .setMpi(mpiProto)
            .setTopology(topologyProto)
            .setNotificationCount(cfg.getNotificationCount())
            .setRepeat(cfg.getRepeat())
            .build();
    }

    public BenchmarkConfig getConfig() {
        return cfg;
    }

    private void dumpConfig() {
        log.info("mpi_hosts = {}",
                 cfg.getMpiHosts());
        log.info("testbed = {}",
                 cfg.getTestbed());
        log.info("topology_tenants = {}",
                 cfg.getTopologyTenants());
        log.info("topology_bridges_per_tenant = {}",
                 cfg.getTopologyBridgesPerTenant());
        log.info("topology_ports_per_bridge = {}",
                 cfg.getTopologyPortsPerBridge());
        log.info("notification_count = {}",
                 cfg.getNotificationCount());
        log.info("repeat = {}",
                 cfg.getRepeat());
        log.info("bench_output_file = {}",
                 cfg.getOutput());
        log.info("bench_output_bin = {}",
                 cfg.getBinOutput());
        log.info("skip_build_layout = {}",
                 cfg.getSkipBuildLayout());
        log.info("skip_remove_layout = {}",
                 cfg.getSkipRemoveLayout());
        log.info("skip_test_read = {}",
                 cfg.getSkipTestRead());
        log.info("skip_test_read_alltoall = {}",
                 cfg.getSkipTestReadAllToAll());
        log.info("skip_test_update_bridges = {}",
                 cfg.getSkipTestUpdateBridges());
        log.info("skip_test_update_bridges_alltoall = {}",
                 cfg.getSkipTestUpdateBridgesAllToAll());
        log.info("skip_test_update_ports = {}",
                 cfg.getSkipTestUpdatePorts());
        log.info("skip_test_update_ports_alltoall = {}",
                 cfg.getSkipTestUpdatePortsAllToAll());
        log.info("skip_test_notify_bridge = {}",
                 cfg.getSkipTestNotifyBridge());
        log.info("skip_test_notify_bridge_creation = {}",
                 cfg.getSkipTestNotifyBridgeCreation());
        log.info("skip_test_read_bridges_chunk = {}",
                 cfg.getSkipTestReadBridgesChunk());
        log.info("skip_test_read_ports_chunk = {}",
                 cfg.getSkipTestReadPortsChunk());
    }

    private void logStep(Comp step) {
        if (isMpiRoot()) {
            log.info("STEP: " + step.name);
        }
    }

    private void initializeBenchmark() throws MPIException, StorageException {
        logStep(Comp.STEP_INITIALIZING);
        if (isMpiRoot()) {
            dumpConfig();
            Router providerRouter = new Router();
            providerRouter.setName("provider");
            store.create(providerRouter);
            providerRouterId = broadcastUUID(providerRouter.getId(), mpiRoot);
        } else {
            providerRouterId = broadcastUUID(null, mpiRoot);
        }
    }

    private void finalizeBenchmark() throws StorageException {
        logStep(Comp.STEP_FINALIZING);
        if (isMpiRoot()) {
            BenchModels.Benchmark benchOutput =
                benchmarkProto.toBuilder()
                              .addAllData(benchdata)
                              .build();
            outStream.println(benchOutput);
            outStream.flush();
            if (binStream != null) {
                try {
                    benchOutput.writeTo(binStream);
                    binStream.flush();
                } catch (java.io.IOException e) {
                    log.warn("cannot write binary output data", e);
                }
            }
        }
    }

    public void run(String[] args) throws Exception {
        initializeBenchmark();
        if (!cfg.getSkipBuildLayout()) {
            buildLayout();
            if (!cfg.getSkipTestRead())
                testRead();
            if (!cfg.getSkipTestReadAllToAll())
                testReadAllToAll();
            if (!cfg.getSkipTestReadBridgesChunk())
                testReadBridgesChunk();
            if (!cfg.getSkipTestReadPortsChunk())
                testReadPortsChunk();
            if (!cfg.getSkipTestUpdateBridges())
                testUpdateBridges();
            if (!cfg.getSkipTestUpdateBridgesAllToAll())
                testUpdateBridgesAllToAll();
            if (!cfg.getSkipTestUpdatePorts())
                testUpdatePorts();
            if (!cfg.getSkipTestUpdatePortsAllToAll())
                testUpdatePortsAllToAll();
            if (!cfg.getSkipRemoveLayout())
                removeLayout();
        }

        if (!cfg.getSkipTestNotifyBridge())
            testNotifyBridge();
        if (!cfg.getSkipTestNotifyBridgeCreation())
            testNotifyBridgeCreation();

        finalizeBenchmark();
    }

    /**
     * Record experiment data buffer containing application-wide results
     */
    private void addGlobalData(String name, long value) {
        benchdata.add(BenchModels.ExpData
                          .newBuilder()
                          .setName(name)
                          .setIvalue(value)
                          .build());
    }

    /**
     * Record experiment data buffer containing per-process results
     */
    private void addProcessData(String name, double value, int processRank) {
        benchdata.add(BenchModels.ExpData.newBuilder()
            .setName(name)
            .setTag(String.format("mpiRank=%d", processRank))
            .setFvalue(value)
            .build());
    }

    /**
     * Record data buffers containing per-process elapsed times and
     * ratios (# of operations per unit of time) per operation.
     */
    private void addProcessTimes(Comp component, Op operation,
                                 int processWorkCount,
                                 double processElapsedTime,
                                 int processRank) {
        if (processWorkCount > 0) {
            addProcessData(
                valueName(component, operation, Unit.TIME),
                processElapsedTime,
                processRank);
            addProcessData(
                valueName(component, operation, Unit.RATIO),
                processWorkCount * 1000.0 / processElapsedTime,
                processRank);
        }
    }

    /**
     * Add measured times to experiment data
     * @param step the step collecting the measurements
     * @param tsStart is the per-process timestamp at the beginning of the step
     * @param tsEnd is the per-process timestamp and the end of the step
     */
    private void collectAndPublishStepTimes(Comp step, long tsStart, long tsEnd)
        throws MPIException {

        // Collect global start-end timestamps
        long minTStart = reduceMin(tsStart, mpiRoot);
        long maxTEnd = reduceMax(tsEnd, mpiRoot);

        // Record global step times
        if (isMpiRoot()) {
            long elapsed = maxTEnd - minTStart;
            addGlobalData(
                valueName(step, Op.ELAPSED, Unit.TIME), elapsed);
        }
    }

    private void buildLayout() throws MPIException, StorageException {
        final Comp STEP = Comp.STEP_BUILDLAYOUT;
        logStep(STEP);

        // Distribute work
        MPIBlockDistribution tnWork = new MPIBlockDistribution(
            cfg.getTopologyTenants());
        UUID localTenants[] = new UUID[tnWork.getLocalArraySize()];

        MPIBlockDistribution brWork =  new MPIBlockDistribution(
            cfg.getTopologyTenants() * cfg.getTopologyBridgesPerTenant());
        UUID localBridges[] = new UUID[brWork.getLocalArraySize()];

        MPIBlockDistribution pWork = new MPIBlockDistribution(
            brWork.getGlobalCount() * cfg.getTopologyPortsPerBridge());
        UUID localPorts[] = new UUID[pWork.getLocalArraySize()];

        barrier();

        // Keep track of global time
        long stepTStampStart = getTimeStamp();
        MPITimer stepTimer = new MPITimer();

        // Create tenants
        MPITimer tnTimer = new MPITimer();
        for (int i = 0; i < tnWork.getLocalCount(); i++) {
            Router tn = new Router();
            tn.setName("tn" + tnWork.getGlobalOffset(i));
            store.create(tn);
            localTenants[i] = tn.getId();
            store.connectToRouter(providerRouterId, tn.getId());
        }
        tnTimer.freeze();

        // Distribute tenant ids:
        allTenants = new UUID[tnWork.getGlobalCount()];
        tnWork.compactGlobalArray(allgatherUUID(localTenants), allTenants);

        // Create bridges
        MPITimer brTimer = new MPITimer();
        for (int i = 0; i < brWork.getLocalCount(); i++) {
            int tenantIdx = brWork.getGlobalOffset(i) /
                            cfg.getTopologyBridgesPerTenant();
            Bridge br = new Bridge();
            br.setName("br" + brWork.getGlobalOffset(i));
            store.create(br);
            localBridges[i] = br.getId();
            store.connectToBridge(allTenants[tenantIdx], br.getId());
        }
        brTimer.freeze();

        // Retrieve all bridge ids for later phases
        allBridges = new UUID[brWork.getGlobalCount()];
        brWork.compactGlobalArray(allgatherUUID(localBridges), allBridges);

        // Create ports
        MPITimer pTimer = new MPITimer();
        for (int i = 0; i < pWork.getLocalCount(); i++) {
            int bridgeIdx = pWork.getGlobalOffset(i) /
                            cfg.getTopologyPortsPerBridge();
            BridgePort p = store.attachNewPortToBridge(allBridges[bridgeIdx]);
            localPorts[i] = p.getId();
        }
        pTimer.freeze();

        // End phase timers
        stepTimer.freeze();
        long stepTStampEnd = getTimeStamp();

        // Retrieve all port ids for later phases
        allPorts = new UUID[pWork.getGlobalCount()];
        pWork.compactGlobalArray(allgatherUUID(localPorts), allPorts);

        // Collect and publish times
        collectAndPublishStepTimes(STEP, stepTStampStart, stepTStampEnd);
        double[] localTimes = {tnTimer.msecs(), brTimer.msecs(), pTimer.msecs(),
                               stepTimer.msecs()};
        double[] allTimes = gather(localTimes, mpiRoot);
        if (isMpiRoot()) {
            Op op = Op.CREATE;
            for (int i = 0; i < mpiSize; i++) {
                int timeIdx = i * localTimes.length;
                addProcessTimes(Comp.TENANT, op, tnWork.getItemCount(i),
                                allTimes[timeIdx++], i);
                addProcessTimes(Comp.BRIDGE, op, brWork.getItemCount(i),
                                allTimes[timeIdx++], i);
                addProcessTimes(Comp.BRIDGEPORT, op, pWork.getItemCount(i),
                                allTimes[timeIdx++], i);
                addProcessData(valueName(Comp.LAYOUT, op, Unit.TIME),
                               allTimes[timeIdx++], i);
            }
        }
    }

    private void testRead() throws MPIException, StorageException {
        final Comp STEP = Comp.STEP_TESTREAD;
        logStep(STEP);

        MPIBlockDistribution tnWork =
            new MPIBlockDistribution(cfg.getTopologyTenants());
        MPIBlockDistribution brWork =  new MPIBlockDistribution(
            cfg.getTopologyTenants() * cfg.getTopologyBridgesPerTenant());
        MPIBlockDistribution pWork =  new MPIBlockDistribution(
            brWork.getGlobalCount() * cfg.getTopologyPortsPerBridge());

        barrier();

        long stepTStampStart = getTimeStamp();
        MPITimer stepTimer = new MPITimer();

        MPITimer tnTimer = new MPITimer();
        for (int k = 0; k < cfg.getRepeat(); k++) {
            for (int i = 0; i < tnWork.getLocalCount(); i++) {
                store.get(Router.class, allTenants[tnWork.getGlobalOffset(i)]);
            }
        }
        tnTimer.freeze();

        MPITimer brTimer = new MPITimer();
        for (int k = 0; k < cfg.getRepeat(); k++) {
            for (int i = 0; i < brWork.getLocalCount(); i++) {
                store.get(Bridge.class, allBridges[brWork.getGlobalOffset(i)]);
            }
        }
        brTimer.freeze();

        MPITimer pTimer = new MPITimer();
        for (int k = 0; k < cfg.getRepeat(); k++) {
            for (int i = 0; i < pWork.getLocalCount(); i++) {
                store.get(Port.class, allPorts[pWork.getGlobalOffset(i)]);
            }
        }
        pTimer.freeze();

        stepTimer.freeze();
        long stepTStampEnd = getTimeStamp();

        // Collect and publish times
        collectAndPublishStepTimes(STEP, stepTStampStart, stepTStampEnd);
        double[] localTimes = {tnTimer.msecs(), brTimer.msecs(), pTimer.msecs(),
                               stepTimer.msecs()};
        double[] allTimes = gather(localTimes, mpiRoot);
        if (isMpiRoot()) {
            Op op = Op.READ;
            for (int i = 0; i < mpiSize; i++) {
                int timeIdx = i * localTimes.length;
                addProcessTimes(Comp.TENANT, op,
                                tnWork.getItemCount(i) * cfg.getRepeat(),
                                allTimes[timeIdx++], i);
                addProcessTimes(Comp.BRIDGE, op,
                                brWork.getItemCount(i) * cfg.getRepeat(),
                                allTimes[timeIdx++], i);
                addProcessTimes(Comp.BRIDGEPORT, op,
                                pWork.getItemCount(i) * cfg.getRepeat(),
                                allTimes[timeIdx++], i);
                addProcessData(valueName(Comp.LAYOUT, op, Unit.TIME),
                               allTimes[timeIdx++], i);
            }
        }
    }

    private void testReadAllToAll() throws MPIException, StorageException {
        final Comp STEP = Comp.STEP_TESTREADALLTOALL;
        logStep(STEP);

        barrier();

        long stepTStampStart = getTimeStamp();
        MPITimer stepTimer = new MPITimer();

        MPITimer tnTimer = new MPITimer();
        for (int k = 0; k < cfg.getRepeat(); k++) {
            for (UUID r: allTenants) {
                store.get(Router.class, r);
            }
        }
        tnTimer.freeze();

        MPITimer brTimer = new MPITimer();
        for (int k = 0; k < cfg.getRepeat(); k++) {
            for (UUID b: allBridges) {
                store.get(Bridge.class, b);
            }
        }
        brTimer.freeze();

        MPITimer pTimer = new MPITimer();
        for (int k = 0; k < cfg.getRepeat(); k++) {
            for (UUID p: allPorts) {
                store.get(Port.class, p);
            }
        }
        pTimer.freeze();

        stepTimer.freeze();
        long stepTStampEnd = getTimeStamp();

        // Collect and publish times
        collectAndPublishStepTimes(STEP, stepTStampStart, stepTStampEnd);
        double[] localTimes =
            {tnTimer.msecs(), brTimer.msecs(), pTimer.msecs(),
             stepTimer.msecs()};
        double[] allTimes = gather(localTimes, mpiRoot);
        if (isMpiRoot()) {
            Op op = Op.READALL;
            for (int i = 0; i < mpiSize; i++) {
                int timeIdx = i * localTimes.length;
                addProcessTimes(Comp.TENANT, op,
                                allTenants.length * cfg.getRepeat(),
                                allTimes[timeIdx++], i);
                addProcessTimes(Comp.BRIDGE, op,
                                allBridges.length * cfg.getRepeat(),
                                allTimes[timeIdx++], i);
                addProcessTimes(Comp.BRIDGEPORT, op,
                                allPorts.length * cfg.getRepeat(),
                                allTimes[timeIdx++], i);
                addProcessData(valueName(Comp.LAYOUT, op, Unit.TIME),
                               allTimes[timeIdx++], i);
            }
        }
    }

    private void testReadBridgesChunk() throws MPIException, StorageException {
        final Comp STEP = Comp.STEP_TESTREADBRIDGESCHUNK;
        logStep(STEP);

        barrier();

        long stepTStampStart = getTimeStamp();
        MPITimer stepTimer = new MPITimer();

        List<Bridge> brList = null;
        for (int k = 0; k < cfg.getRepeat(); k++) {
            brList = store.getAll(Bridge.class);
        }

        stepTimer.freeze();
        long stepTStampEnd = getTimeStamp();

        // Collect and publish times
        collectAndPublishStepTimes(STEP, stepTStampStart, stepTStampEnd);
        double[] localTimes = {stepTimer.msecs()};
        double[] allTimes = gather(localTimes, mpiRoot);
        if (isMpiRoot() && brList != null) {
            for (int i = 0; i < mpiSize; i++) {
                int timeIdx = i * localTimes.length;
                addProcessTimes(Comp.BRIDGE, Op.READCHUNK,
                                brList.size() * cfg.getRepeat(),
                                allTimes[timeIdx++], i);
            }
        }
    }

    private void testReadPortsChunk() throws MPIException, StorageException {
        final Comp STEP = Comp.STEP_TESTREADPORTSCHUNK;
        logStep(STEP);

        barrier();

        long stepTStampStart = getTimeStamp();
        MPITimer stepTimer = new MPITimer();

        List<BridgePort> pList = null;
        for (int k = 0; k < cfg.getRepeat(); k++) {
            pList = store.getAll(BridgePort.class);
        }

        stepTimer.freeze();
        long stepTStampEnd = getTimeStamp();

        // Collect and publish times
        collectAndPublishStepTimes(STEP, stepTStampStart, stepTStampEnd);
        double[] localTimes = {stepTimer.msecs()};
        double[] allTimes = gather(localTimes, mpiRoot);
        if (isMpiRoot() && pList != null) {
            for (int i = 0; i < mpiSize; i++) {
                int timeIdx = i * localTimes.length;
                addProcessTimes(Comp.BRIDGEPORT, Op.READCHUNK,
                                pList.size() * cfg.getRepeat(),
                                allTimes[timeIdx++], i);
            }
        }
    }

    private void testUpdateBridges() throws MPIException, StorageException {
        final Comp STEP = Comp.STEP_TESTUPDATEBRIDGES;
        logStep(STEP);

        MPIBlockDistribution brWork =
            new MPIBlockDistribution(allBridges.length);

        barrier();

        long stepTStampStart = getTimeStamp();
        MPITimer stepTimer = new MPITimer();

        for (int k = 0; k < cfg.getRepeat(); k++) {
            for (int i = 0; i < brWork.getLocalCount(); i++) {
                Bridge b = store.get(Bridge.class,
                                     allBridges[brWork.getGlobalOffset(i)]);
                String newName = String.format("%s_by_%d", b.getName(), mpiRank);
                b.setName(newName);
                store.update(b);
            }
        }

        stepTimer.freeze();
        long stepTStampEnd = getTimeStamp();

        // Collect and publish times
        collectAndPublishStepTimes(STEP, stepTStampStart, stepTStampEnd);
        double[] localTimes = {stepTimer.msecs()};
        double[] allTimes = gather(localTimes, mpiRoot);
        if (isMpiRoot()) {
            for (int i = 0; i < mpiSize; i++) {
                int timeIdx = i * localTimes.length;
                addProcessTimes(Comp.BRIDGE, Op.UPDATE,
                                brWork.getItemCount(i) * cfg.getRepeat(),
                                allTimes[timeIdx++], i);
            }
        }
    }

    private void testUpdateBridgesAllToAll()
        throws MPIException, StorageException {
        final Comp STEP = Comp.STEP_TESTUPDATEBRIDGESALLTOALL;
        logStep(STEP);

        int brCount = allBridges.length;

        barrier();

        long stepTStampStart = getTimeStamp();
        MPITimer stepTimer = new MPITimer();

        for (int k = 0; k < cfg.getRepeat(); k++) {
            for (UUID bId: allBridges) {
                Bridge b = store.get(Bridge.class, bId);
                String newName = String.format("%s_by_%d", b.getName(), mpiRank);
                b.setName(newName);
                store.update(b);
            }
        }

        stepTimer.freeze();
        long stepTStampEnd = getTimeStamp();

        // Collect and publish times
        collectAndPublishStepTimes(STEP, stepTStampStart, stepTStampEnd);
        double[] localTimes = {stepTimer.msecs()};
        double[] allTimes = gather(localTimes, mpiRoot);
        if (isMpiRoot()) {
            for (int i = 0; i < mpiSize; i++) {
                int timeIdx = i * localTimes.length;
                addProcessTimes(Comp.BRIDGE, Op.UPDATEALL,
                                brCount * cfg.getRepeat(),
                                allTimes[timeIdx++], i);
            }
        }
    }

    private void testUpdatePorts() throws MPIException, StorageException {
        final Comp STEP = Comp.STEP_TESTUPDATEPORTS;
        logStep(STEP);

        MPIBlockDistribution pWork =
            new MPIBlockDistribution(allPorts.length);

        barrier();

        long stepTStampStart = getTimeStamp();
        MPITimer stepTimer = new MPITimer();

        for (int k = 0; k < cfg.getRepeat(); k++) {
            for (int i = 0; i < pWork.getLocalCount(); i++) {
                BridgePort p = store.get(BridgePort.class,
                                     allPorts[pWork.getGlobalOffset(i)]);
                UUID vif_id = new UUID(mpiRank * k + 1, i);
                p.setProperty(Port.Property.vif_id, vif_id.toString());
                store.update(p);
            }
        }

        stepTimer.freeze();
        long stepTStampEnd = getTimeStamp();

        // Collect and publish times
        collectAndPublishStepTimes(STEP, stepTStampStart, stepTStampEnd);
        double[] localTimes = {stepTimer.msecs()};
        double[] allTimes = gather(localTimes, mpiRoot);
        if (isMpiRoot()) {
            for (int i = 0; i < mpiSize; i++) {
                int timeIdx = i * localTimes.length;
                addProcessTimes(Comp.BRIDGEPORT, Op.UPDATE,
                                pWork.getItemCount(i) * cfg.getRepeat(),
                                allTimes[timeIdx++], i);
            }
        }
    }

    private void testUpdatePortsAllToAll()
        throws MPIException, StorageException {
        final Comp STEP = Comp.STEP_TESTUPDATEPORTSALLTOALL;
        logStep(STEP);

        int pCount = allPorts.length;

        barrier();

        long stepTStampStart = getTimeStamp();
        MPITimer stepTimer = new MPITimer();

        for (int k = 0; k < cfg.getRepeat(); k++) {
            for (int i = 0; i < pCount; i++) {
                BridgePort p = store.get(BridgePort.class, allPorts[i]);
                UUID vif_id = new UUID(mpiRank * k + 1, i);
                p.setProperty(Port.Property.vif_id, vif_id.toString());
                store.update(p);
            }
        }

        stepTimer.freeze();
        long stepTStampEnd = getTimeStamp();

        // Collect and publish times
        collectAndPublishStepTimes(STEP, stepTStampStart, stepTStampEnd);
        double[] localTimes = {stepTimer.msecs()};
        double[] allTimes = gather(localTimes, mpiRoot);
        if (isMpiRoot()) {
            for (int i = 0; i < mpiSize; i++) {
                int timeIdx = i * localTimes.length;
                addProcessTimes(Comp.BRIDGEPORT, Op.UPDATEALL,
                                pCount * cfg.getRepeat(),
                                allTimes[timeIdx++], i);
            }
        }
    }

    private void testNotifyBridge() throws MPIException, StorageException,
                                            InterruptedException {
        final Comp STEP = Comp.STEP_TESTNOTIFYBRIDGE;
        logStep(STEP);

        // create tenant router and bridge for notifications, and
        // distribute to all processes
        final UUID tnId;
        final UUID targetId;
        if (isMpiRoot()) {
            Router tn = new Router();
            tn.setName("tn_ntfy_bridge");
            store.create(tn);
            tnId = tn.getId();
            Bridge br = new Bridge();
            br.setName("br_ntfy_bridge");
            store.create(br);
            store.connectToBridge(tn.getId(), br.getId());
            targetId = broadcastUUID(br.getId(), mpiRoot);
        } else {
            tnId = null;
            targetId = broadcastUUID(null, mpiRoot);
        }
        Bridge target = store.get(Bridge.class, targetId);

        // Setup notification watching environment
        final String targetName =
            String.format("bridge_notified_%d", cfg.getNotificationCount());
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicInteger errors = new AtomicInteger(0);

        Observer<Bridge> obs = new Observer<Bridge>() {
            @Override
            public void onError(Throwable e)  {
                errors.incrementAndGet();
                done.countDown();
            }
            @Override
            public void onCompleted() {
                errors.incrementAndGet();
                done.countDown();
            }
            @Override
            public void onNext(Bridge b) {
                if (b == null || !targetId.equals(b.getId()) ||
                    !targetName.equals(b.getName())) {
                    return;
                }
                done.countDown();
            }
        };

        barrier();

        long stepTStampStart = getTimeStamp();
        MPITimer stepTimer = new MPITimer();

        // after subscription, we should receive initial state + updates
        rx.Subscription subs = store.subscribe(targetId, obs);

        // generate notifications
        if (isMpiRoot()) {
            for (int k = 1; k <= cfg.getNotificationCount(); k++) {
                String newName =  String.format("bridge_notified_%d", k);
                target.setName(newName);
                store.update(target);
            }
        }

        // wait for notifications
        done.await();

        stepTimer.freeze();
        long stepTStampEnd = getTimeStamp();

        // cleanup
        subs.unsubscribe();
        if (isMpiRoot()) {
            store.delete(Router.class, tnId);
            store.delete(Bridge.class, targetId);
        }

        // Collect and publish times
        long globalErrors = reduceSum(errors.get(), mpiRoot);
        collectAndPublishStepTimes(STEP, stepTStampStart, stepTStampEnd);
        double[] localTimes = {stepTimer.msecs()};
        double[] allTimes = gather(localTimes, mpiRoot);
        if (isMpiRoot() && globalErrors == 0) {
            for (int i = 0; i < mpiSize; i++) {
                int timeIdx = i * localTimes.length;
                addProcessTimes(Comp.BRIDGE, Op.NOTIFY,
                                cfg.getNotificationCount(),
                                allTimes[timeIdx++], i);
            }
        }
    }

    private void testNotifyBridgeCreation()
        throws MPIException, StorageException, InterruptedException {
        final Comp STEP = Comp.STEP_TESTNOTIFYBRIDGECREATION;
        logStep(STEP);

        final AtomicInteger created = new AtomicInteger(0);
        final AtomicInteger deleted = new AtomicInteger(0);
        final AtomicInteger errors = new AtomicInteger(0);
        final Object nudge = new Object();

        Observer<EntityIdSetEvent<UUID>> obs =
            new Observer<EntityIdSetEvent<UUID>>() {
            @Override
            public void onError(Throwable e)  {
                log.error(STEP + " Observer", e);
                errors.incrementAndGet();
                nudge.notifyAll();
            }
            @Override
            public void onCompleted() {
                errors.incrementAndGet();
                nudge.notifyAll();
            }
            @Override
            public void onNext(EntityIdSetEvent<UUID> event) {
                if (event.type == EntityIdSetEvent.Type.CREATE) {
                    created.incrementAndGet();
                    synchronized (nudge) {
                        nudge.notifyAll();
                    }
                } else if (event.type == EntityIdSetEvent.Type.DELETE) {
                    deleted.incrementAndGet();
                    synchronized (nudge) {
                        nudge.notifyAll();
                    }
                }
            }
        };

        barrier();

        // after subscription, we should receive create/delete notifications
        rx.Subscription subs = store.subscribeAll(Bridge.class, obs);

        // wait to purge initial state dump (Zoom version of the benchmark
        // storage service does not distinguish between state dumps and
        // creations)
        Thread.sleep(10000);

        barrier();

        // reset counters
        created.set(0);
        deleted.set(0);
        errors.set(0);

        barrier();

        long stepTStampStart = getTimeStamp();
        MPITimer stepTimer = new MPITimer();

        // create and destory bridges
        if (isMpiRoot()) {
            for (int k = 0; k < cfg.getNotificationCount(); k++) {
                Bridge bridge = new Bridge();
                bridge.setName("tmp_bridge" + k);
                store.create(bridge);
                store.delete(Bridge.class, bridge.getId());
            }
        }

        // wait for notifications
        synchronized(nudge) {
            while (errors.get() == 0 &&
                  (created.get() < cfg.getNotificationCount() ||
                   deleted.get() < cfg.getNotificationCount())) {
                nudge.wait();
            }
        }

        stepTimer.freeze();
        long stepTStampEnd = getTimeStamp();

        // cleanup
        subs.unsubscribe();

        // Collect and publish times
        long globalErrors = reduceSum(errors.get(), mpiRoot);
        collectAndPublishStepTimes(STEP, stepTStampStart, stepTStampEnd);
        double[] localTimes = {stepTimer.msecs()};
        double[] allTimes = gather(localTimes, mpiRoot);
        if (isMpiRoot() && globalErrors == 0) {
            for (int i = 0; i < mpiSize; i++) {
                int timeIdx = i * localTimes.length;
                addProcessTimes(Comp.BRIDGE, Op.NOTIFYCREATION,
                                2 * cfg.getNotificationCount(),
                                allTimes[timeIdx++], i);
            }
        }
    }

    private void removeLayout() throws MPIException, StorageException {
        final Comp STEP = Comp.STEP_REMOVELAYOUT;
        logStep(STEP);

        MPIBlockDistribution tnWork = new MPIBlockDistribution(
            cfg.getTopologyTenants());
        MPIBlockDistribution brWork =  new MPIBlockDistribution(
            cfg.getTopologyTenants() * cfg.getTopologyBridgesPerTenant());
        MPIBlockDistribution pWork =  new MPIBlockDistribution(
            brWork.getGlobalCount() * cfg.getTopologyPortsPerBridge());

        barrier();

        long stepTStampStart = getTimeStamp();
        MPITimer stepTimer = new MPITimer();

        MPITimer pTimer = new MPITimer();
        for (int i = 0; i < pWork.getLocalCount(); i++) {
            store.delete(Port.class, allPorts[pWork.getGlobalOffset(i)]);
        }
        pTimer.freeze();

        barrier();

        MPITimer brTimer = new MPITimer();
        for (int i = 0; i < brWork.getLocalCount(); i++) {
            store.delete(Bridge.class, allBridges[brWork.getGlobalOffset(i)]);
        }
        brTimer.freeze();

        barrier();

        MPITimer tnTimer = new MPITimer();
        for (int i = 0; i < tnWork.getLocalCount(); i++) {
            store.delete(Router.class, allTenants[tnWork.getGlobalOffset(i)]);
        }
        tnTimer.freeze();

        stepTimer.freeze();
        long stepTStampEnd = getTimeStamp();

        // Collect and publish times
        collectAndPublishStepTimes(STEP, stepTStampStart, stepTStampEnd);
        double[] localTimes = {tnTimer.msecs(), brTimer.msecs(), pTimer.msecs(),
                               stepTimer.msecs()};
        double[] allTimes = gather(localTimes, mpiRoot);
        if (isMpiRoot()) {
            Op op = Op.DELETE;
            for (int i = 0; i < mpiSize; i++) {
                int timeIdx = i * localTimes.length;
                addProcessTimes(Comp.TENANT, op, tnWork.getItemCount(i),
                                allTimes[timeIdx++], i);
                addProcessTimes(Comp.BRIDGE, op, brWork.getItemCount(i),
                                allTimes[timeIdx++], i);
                addProcessTimes(Comp.BRIDGEPORT, op, pWork.getItemCount(i),
                                allTimes[timeIdx++], i);
                addProcessData(valueName(Comp.LAYOUT, op, Unit.TIME),
                               allTimes[timeIdx++], i);
            }
        }
    }
}
