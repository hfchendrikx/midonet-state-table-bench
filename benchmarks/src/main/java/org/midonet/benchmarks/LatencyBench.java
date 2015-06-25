package org.midonet.benchmarks;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.I0Itec.zkclient.ZkClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rx.observers.TestObserver;

import org.midonet.cluster.data.storage.ArpMergedMap;
import org.midonet.cluster.data.storage.KafkaBus;
import org.midonet.midolman.layer3.Route;
import org.midonet.midolman.serialization.SerializationException;
import org.midonet.midolman.state.ArpCacheEntry;
import org.midonet.packets.IPv4Addr;
import org.midonet.packets.MAC;

import kafka.utils.ZKStringSerializer$;

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

    public LatencyBench(StorageType storageType, int size, int writeCount,
                        ZkClient zkclient)
        throws Exception {
        super(storageType, size, zkclient);
        this.dataSize = size;
        this.writeCount = writeCount;
    }

    private List<Long> arpMergedMapBench(int opCount) throws Exception {
        List<Long> latencies = new LinkedList<>();

        TestObserver obs = ArpMergedMap.arpMapObserver(arpMergedMap);
        /* The merged map observable first emits its content */
        int mapSize = arpMergedMap.size();
        ArpMergedMap.awaitForObserverEvents(obs, mapSize, 30000 /*timeout*/);

        for (int i = 0; i < opCount; i++) {
            long start = System.nanoTime();
            IPv4Addr ip = randomExistingIP();
            arpMergedMap.putOpinion(ip, randomArpEntry());
            ArpMergedMap.awaitForObserverEvents(obs, mapSize + i,
                                                5000 /* timeout */);
            long end = System.nanoTime();
            latencies.add(end-start);
        }
        return latencies;
    }

    private List<Long> arpBench(int opCount) throws InterruptedException {
        List<Long> latencies = new LinkedList<>();
        ReplicatedMapWatcher<IPv4Addr, ArpCacheEntry> arpWatcher =
            new ReplicatedMapWatcher<>();
        arpTable.addWatcher(arpWatcher);

        for (int i = 0; i < opCount; i++) {
            long start = System.nanoTime();
            IPv4Addr ip = randomExistingIP();
            arpTable.put(ip, randomArpEntry());
            arpWatcher.waitForResult(0 /* wait until notified */);
            long end = System.nanoTime();
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
            long start = System.nanoTime();
            MAC mac = randomExistingMAC();
            macTable.put(mac, UUID.randomUUID());
            macWatcher.waitForResult(0 /* wait until notified */);
            long end = System.nanoTime();
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
            long start = System.nanoTime();
            if (rnd.nextInt(2) == 0) {
                Route route = removeRndRoute();
                routeSet.remove(route);

            } else {
                Route route = randomRoute();
                routeSet.add(route);
                routes.add(route);
            }
            routeWatcher.waitForResult(0 /* wait until notified */);
            long end = System.nanoTime();
            latencies.add(end - start);
        }
        return latencies;
    }

    private void warmup(int opCount) throws Exception {
        switch (storageType) {
            case ARP_TABLE:
                log.info("Warming-up replicated ARP table with {} updates",
                         opCount);
                arpBench(opCount);
                break;
            case ARP_MERGED_MAP:
                log.info("Warming-up ARP merged map with {} updates",
                         opCount);
                arpMergedMapBench(opCount);
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
                case ARP_MERGED_MAP:
                    latencies = arpMergedMapBench(writeCount);
                    break;
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
        if (args.length == 3) {
//            try {
//                MPI.Init(args);
//            } catch (Exception e) {
//                log.error("Impossible to initialize MPI", e);
//            }

            StorageType type = StorageType.valueOf(args[0]);
            int dataSize = Integer.parseInt(args[1]);
            int writeCount = Integer.parseInt(args[2]);
            String configFile = System.getProperty("midobench.config");
            log.info("Starting experiment with config file: {} state: {} "
                     + "size: {} #writes: {}", configFile, type, dataSize,
                     writeCount);

            try {
                //Injector injector = MapSetBenchmark.createInjector(configFile);
//                String mpiHosts = getMpiHosts(configFile);


                LatencyBench bench =
                    new LatencyBench(type, dataSize, writeCount,
                                     KafkaBus.zookeeperClient());
                bench.run();
            } catch (Exception e) {
                log.error("An exception was caught during the benchmark", e);
            }
            System.exit(0);

        } else {
            log.error("Please specify the data structure "
                      + "type (MAC_TABLE, ARP_TABLE, or ROUTING_TABLE), the "
                      + "size of the data structure, and the number of writes "
                      + "to issue");
        }
    }
}
