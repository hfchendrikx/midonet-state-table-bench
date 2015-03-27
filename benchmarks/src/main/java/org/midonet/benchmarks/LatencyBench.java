package org.midonet.benchmarks;

import org.apache.zookeeper.KeeperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.midonet.packets.IPv4Addr;

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

    protected void run() throws InterruptedException {
        populateArpTable(arpTable);

        double start = System.currentTimeMillis();
        for (int i = 0; i < writeCount; i++) {
            switch(storageType) {
                case ARP_TABLE:
                    IPv4Addr ip = randomExistingIP();
                    arpTable.put(ip, randomArpEntry());
                    arpTable.addWatcher(arpWatcher);
                    arpWatcher.waitForResult();
                    arpWatcher.resetLatch();
                    break;

                case MAC_TABLE:
                    break;
                case ROUTING_TABLE:
                    break;
            }
        }
        double end = System.currentTimeMillis();
        double avgLatency = (end - start) / ((double) writeCount);
        results.put("Avg. Latency in ms", avgLatency);

        printResults(log);
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
