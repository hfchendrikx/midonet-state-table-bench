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
import mpi.MPIException;

/**
 * This class implements the Churn benchmark which consists in measuring the
 * average latency between the update of an entry in a replicated map/set
 * and the reception of the updated map/set by a set of clients, for a given
 * rate of update and map/set size.
 */
public class ChurnBench extends MapSetBenchmark {
    private static final Logger log =
        LoggerFactory.getLogger(ChurnBench.class);

    /**
     * These dummy IP, MAC, and Route are the keys for which their associated
     * value will be used to encode the map/set version. In doing so, clients
     * receiving a map/set will be able to know its version. The updater
     * records the current time before writing a given map/set version and
     * so do the clients when receiving maps/sets. As a consequence, we are
     * able to compute the latency between an update and its reception by all
     * clients.
     */
    private static IPv4Addr DUMMY_IP = new IPv4Addr("0.0.0.0");
    private static MAC DUMMY_MAC = new MAC(0l);
    private static Route DUMMY_ROUTE = new Route(0, 0, 0, 0, Route.NextHop.PORT,
                                                 new UUID(0, 0), 0, 0, "",
                                                 new UUID(0, 0));

    /**
     * The maxium time during which a client waits for an update. Passed this time,
     * the client will consider the benchmark over.
     */
    private static long CLIENT_TIMEOUT = 5000;

    private int dataSize;
    private int writeCount;
    // Update rate in writes per second
    private int writeRate;

    // For each map/set version, we record a time.
    // For the updater, this is the time when the update was issued.
    // For the client, this is the reception time of the table version.
    private long[] versionTimestamps;

    public ChurnBench(Injector injector, String mpiHosts,
                      StorageType storageType, int size, int writeCount,
                      int writeRate) throws Exception {
        super(storageType, injector, mpiHosts, size);
        this.dataSize = size;
        this.writeCount = writeCount;
        this.writeRate = writeRate;
        this.versionTimestamps = new long[writeCount];
    }

    private void arpBench(int opCount, boolean warmup) throws InterruptedException {
        // The process with rank 0 is the updater
        if (mpiRank == 0) {
            int sleepTime = 1000 / writeRate;
            for (int i = 0; i < opCount; i++) {
                if (!warmup)
                    versionTimestamps[i] = System.currentTimeMillis();

                // We encode the table version in the expiry field
                // of the arp entry.
                ArpCacheEntry arpEntry =
                    new ArpCacheEntry(MAC.random(), i /*expiry*/,
                                      0 /*stale*/, 0 /*lastArp*/);
                arpTable.put(randomExistingIP(), arpEntry);
                Thread.sleep(sleepTime);
            }

        // Otherwise, the process is a client
        } else {
            ReplicatedMapWatcher<IPv4Addr, ArpCacheEntry> arpWatcher =
                new ReplicatedMapWatcher<>();
            arpTable.addWatcher(arpWatcher);

            int lastVersion = -1;
            boolean done = false;

            do {
                arpWatcher.waitForResult(CLIENT_TIMEOUT);
                ArpCacheEntry arpEntry = arpTable.get(DUMMY_IP);
                int version = (int) arpEntry.expiry;

                // If we get the same version, then we timed-out on the watcher
                // and thus the benchmark is over.
                if (version == lastVersion) {
                    done = true;
                } else {
                    if (!warmup)
                        versionTimestamps[version] = System.currentTimeMillis();
                }
            } while (done);
        }
    }

    private void macBench(int opCount, boolean warmup) {
        //TODO
    }

    private void routeBench(int opCount, boolean warmup) {
        //TODO
    }

    private void collectResults() throws MPIException {
        List<Long> latencies = new LinkedList<>();
        long[] results = gather(versionTimestamps, mpiRoot);

        if (isMpiRoot()) {
            // Compute latencies for versions of the map/set
            // that have a timestamp for all clients.
            for (int version = 0; version < writeCount; version++) {
                boolean allHaveTS = true;
                long latency = 0;
                // The updater timestamp is the timestamp of process with rank 0
                long upateTS = results[version * mpiSize];

                for (int process = 1; process < mpiSize; process++) {
                    long clientTS = results[(version * mpiSize) + process];
                    if (clientTS == 0) {
                        allHaveTS = false;
                        break;
                    } else {
                       latency += (clientTS - upateTS);
                    }
                }

                if (allHaveTS) {
                    latencies.add(latency / (mpiSize - 1));
                }
            }
        }

        computeStats(latencies);
        printResults(log);
    }

    private void warmup(int opCount) throws InterruptedException,
                                            SerializationException {
        switch (storageType) {
            case ARP_TABLE:
                log.info("Warming-up replicated ARP table with {} updates",
                         opCount);
                arpBench(opCount, true /*warmup*/);
                break;
            case MAC_TABLE:
                log.info("Warming-up replicated MAC table with {} updates",
                         opCount);
                macBench(opCount, true /*warmup*/);
                break;
            case ROUTING_TABLE:
                log.info("Warming-up replicated routing table with {} updates",
                         opCount);
                routeBench(opCount, true /*warmup*/);
                break;
        }
    }

    public void run() {
        try {
            populateTable();
            warmup(WARMUP_OP_COUNT);

            // Wait for sufficiently long so that clients timeout
            if (isMpiRoot()) {
                Thread.sleep(2 * CLIENT_TIMEOUT);
            }
            // All processes (the updater and the clients) synchronize with this
            // barrier to start the benchmark approximately at the same time.
            barrier();

            switch (storageType) {
                case ARP_TABLE:
                    arpBench(writeCount, false /*warmup*/);
                    break;
                case MAC_TABLE:
                    macBench(writeCount, false /*warmup*/);
                    break;
                case ROUTING_TABLE:
                    routeBench(writeCount, false /*warmup*/);
                    break;
            }
            collectResults();
        } catch(Exception e) {
            log.error("Exception caught when executing Churn benchmark", e);
        }
    }

    public static void main(String[] args) {
        if (args.length == 5) {
            String configFile = args[0];
            StorageType storageType = StorageType.valueOf(args[1]);
            int size = Integer.parseInt(args[2]);
            int writeCount = Integer.parseInt(args[3]);
            int writeRate = Integer.parseInt(args[4]);

            try {
                MPI.Init(args);
                Injector injector = MapSetBenchmark.createInjector(configFile);
                String mpiHosts = getMpiHosts(configFile);
                ChurnBench churnBench = new ChurnBench(injector, mpiHosts,
                                                       storageType, size,
                                                       writeCount, writeRate);
                churnBench.run();
            } catch(Exception e) {
                log.warn("Unable to initialize churn benchmark", e);
            }
        } else {
            log.error("Please specify the config. file, the data structure "
                      + "type (MAC_TABLE, ARP_TABLE, or ROUTING_TABLE), the "
                      + "size of the data structure, the number of writes "
                      + "to perform, and the update rate");
        }
    }
}
