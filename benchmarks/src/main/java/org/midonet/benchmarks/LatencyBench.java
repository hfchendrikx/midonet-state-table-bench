package org.midonet.benchmarks;

import java.util.UUID;

import org.apache.zookeeper.KeeperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.midonet.midolman.layer3.Route;
import org.midonet.midolman.serialization.SerializationException;
import org.midonet.midolman.state.ArpCacheEntry;
import org.midonet.packets.IPv4Addr;
import org.midonet.packets.MAC;

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
    int dataSize;
    int writeCount;

    public LatencyBench(String configPath, StorageType storageType, int size,
                        int writeCount) throws InterruptedException,
                                               KeeperException {
        super(storageType, configPath, size);
        this.dataSize = size;
        this.writeCount = writeCount;
    }

    private IPv4Addr randomExistingIP() {
        return arpTableKeys.get(rnd.nextInt(dataSize));
    }

    private MAC randomExistingMAC() {
        return macTableKeys.get(rnd.nextInt(dataSize));
    }

    private Route randomExistingRoute() {
        return routes.get(rnd.nextInt(dataSize));
    }

    private void populateTable() throws InterruptedException,
                                        SerializationException {
        switch (storageType) {
            case ARP_TABLE:
                populateArpTable();
                break;
            case MAC_TABLE:
                populateMacTable();
                break;
            case ROUTING_TABLE:
                populateRouteSet();
                break;
        }
    }

    private void arpBench() throws InterruptedException {
        ReplicatedMapWatcher<IPv4Addr, ArpCacheEntry> arpWatcher =
            new ReplicatedMapWatcher<>();
        arpTable.addWatcher(arpWatcher);

        for (int i = 0; i < writeCount; i++) {
            IPv4Addr ip = randomExistingIP();
            arpTable.put(ip, randomArpEntry());
            arpWatcher.waitForResult();
            arpWatcher.resetLatch();
        }
    }

    private void macBench() throws InterruptedException {
        ReplicatedMapWatcher<MAC, UUID> macWatcher =
            new ReplicatedMapWatcher<>();
        macTable.addWatcher(macWatcher);

        for (int i = 0; i < writeCount; i++) {
            MAC mac = randomExistingMAC();
            macTable.put(mac, UUID.randomUUID());
            macWatcher.waitForResult();
            macWatcher.resetLatch();
        }
    }

    private void routeBench() throws InterruptedException,
                                     SerializationException {

        ReplicatedSetWatcher routeWatcher = new ReplicatedSetWatcher();
        routeSet.addWatcher(routeWatcher);

        for (int i = 0; i < writeCount; i++) {
            if (rnd.nextInt(2) == 0) {
                Route route = randomExistingRoute();
                routeSet.remove(route);

            } else {
                routeSet.add(randomRoute());
            }
            routeWatcher.waitForResult();
            routeWatcher.resetLatch();
        }
    }

    protected void run() {
        try {
            populateTable();

            double start = System.currentTimeMillis();
            switch (storageType) {
                case ARP_TABLE:
                    arpBench();
                    break;

                case MAC_TABLE:
                    macBench();
                    break;

                case ROUTING_TABLE:
                    routeBench();
                    break;
            }
            double end = System.currentTimeMillis();
            double avgLatency = (end - start) / ((double) writeCount);
            results.put("Avg. Latency in ms", avgLatency);

            printResults(log);
        } catch (Exception e) {
            log.error("Exception caught while running benchmark", e);
        }
    }

    public static void main(String[] args) {
        if (args.length == 4) {
            String configFile = args[0];
            StorageType type = StorageType.valueOf(args[1]);
            int size = Integer.parseInt(args[2]);
            int writeCount = Integer.parseInt(args[3]);
            log.info("Starting experiment with config file: {} state: {} "
                     + "size: {} #writes: {}", configFile, type, size,
                     writeCount);

            try {
                LatencyBench bench = new LatencyBench(configFile, type, size,
                                                      writeCount);
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
