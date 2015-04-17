package org.midonet.benchmarks;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import com.google.inject.Injector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.midonet.midolman.layer3.Route;
import org.midonet.midolman.serialization.SerializationException;
import org.midonet.midolman.state.ArpCacheEntry;
import org.midonet.packets.IPv4Addr;
import org.midonet.packets.MAC;

import mpi.MPI;

/**
 * This class implements the LatencyBench described in the following document:
 * https://docs.google.com/a/midokura.com/document/d/1p4tAg4ejoV8lCoQCPLTFMPLaA8OE2ktgjXLdfPKM3SI.
 *
 * The benchmark consists in initializing a map or a set (representing a
 * mac table, arp table, or routing table) with a given number of
 * elements and having one client overwriting the values in the map and
 * measuring the latency to issue the write operation until the
 * entire map/set is received. Among others this measures this efficiency
 * of ZK's getChildren operation.
 *
 */
public class LatencyBench extends MapSetBenchmark {

    private static final Logger log =
        LoggerFactory.getLogger(LatencyBench.class);
    private int dataSize;
    private int writeCount;

    public LatencyBench(Injector injector, String mpiHosts,
                        StorageType storageType, int size, int writeCount)
        throws Exception {
        super(storageType, injector, mpiHosts, size);
        this.dataSize = size;
        this.writeCount = writeCount;
    }

    private List<Long> arpBench(int opCount) throws InterruptedException {
        List<Long> latencies = new LinkedList<>();
        ReplicatedMapWatcher<IPv4Addr, ArpCacheEntry> arpWatcher =
            new ReplicatedMapWatcher<>();
        arpTable.addWatcher(arpWatcher);

        for (int i = 0; i < opCount; i++) {
            long start = System.currentTimeMillis();
            IPv4Addr ip = randomExistingIP();
            arpTable.put(ip, randomArpEntry());
            arpWatcher.waitForResult(0 /* wait until notified */);
            long end = System.currentTimeMillis();
            latencies.add(end-start);
        }
        return latencies;
    }

    private List<Long> macBench(int opCount) throws InterruptedException {
        List<Long> latencies = new LinkedList<>();
        ReplicatedMapWatcher<MAC, UUID> macWatcher =
            new ReplicatedMapWatcher<>();
        macTable.addWatcher(macWatcher);

        for (int i = 0; i < opCount; i++) {
            long start = System.currentTimeMillis();
            MAC mac = randomExistingMAC();
            macTable.put(mac, UUID.randomUUID());
            macWatcher.waitForResult(0 /* wait until notified */);
            long end = System.currentTimeMillis();
            latencies.add(end - start);
        }
        return latencies;
    }

    private List<Long> routeBench(int opCount) throws InterruptedException,
                                                      SerializationException {

        List<Long> latencies = new LinkedList<>();
        ReplicatedSetWatcher routeWatcher = new ReplicatedSetWatcher();
        routeSet.addWatcher(routeWatcher);

        for (int i = 0; i < opCount; i++) {
            long start = System.currentTimeMillis();
            if (rnd.nextInt(2) == 0) {
                Route route = removeRndRoute();
                routeSet.remove(route);

            } else {
                Route route = randomRoute();
                routeSet.add(route);
                routes.add(route);
            }
            routeWatcher.waitForResult(0 /* wait until notified */);
            long end = System.currentTimeMillis();
            latencies.add(end - start);
        }
        return latencies;
    }

    private void warmup(int opCount) throws InterruptedException,
                                            SerializationException {
        switch (storageType) {
            case ARP_TABLE:
                log.info("Warming-up replicated ARP table with {} updates",
                         opCount);
                arpBench(opCount);
                break;
            case MAC_TABLE:
                log.info("Warming-up replicated MAC table with {} updates",
                         opCount);
                macBench(opCount);
                break;
            case ROUTING_TABLE:
                log.info("Warming-up replicated routing table with {} updates",
                         opCount);
                routeBench(opCount);
                break;
        }
    }

    protected void run() {
        try {
            populateTable();
            warmup(WARMUP_OP_COUNT);
            List<Long> latencies = null;

            log.info("Executing latency benchmark with {} operations",
                     writeCount);

            switch (storageType) {
                case ARP_TABLE:
                    latencies = arpBench(writeCount);
                    break;
                case MAC_TABLE:
                    latencies = macBench(writeCount);
                    break;
                case ROUTING_TABLE:
                    latencies = routeBench(writeCount);
                    break;
            }
            computeStats(latencies);
            printResults(log);
        } catch (Exception e) {
            log.error("Exception caught while running benchmark", e);
        }
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            try {
                MPI.Init(args);
            } catch (Exception e) {
                log.error("Impossible to initialize MPI", e);
            }

            String configFile = "benchmarks/conf/midobench.conf"; //args[0];
            StorageType type = StorageType.ARP_TABLE; //StorageType.valueOf(args[1]);
            int dataSize = 100; //Integer.parseInt(args[2]);
            int writeCount = 100; //Integer.parseInt(args[3]);
            log.info("Starting experiment with config file: {} state: {} "
                     + "size: {} #writes: {}", configFile, type, dataSize,
                     writeCount);

            try {
                Injector injector = MapSetBenchmark.createInjector(configFile);
                String mpiHosts = getMpiHosts(configFile);
                LatencyBench bench = new LatencyBench(injector, mpiHosts, type,
                                                      dataSize, writeCount);
                bench.run();
            } catch (Exception e) {
                log.error("Exception {} was caught during the benchmark", e);
            }

        } else {
            log.error("Please specify the config. file, the data structure "
                      + "type (MAC_TABLE, ARP_TABLE, or ROUTING_TABLE), the "
                      + "size of the data structure, and the number of writes "
                      + "to issue");
        }
    }
}
