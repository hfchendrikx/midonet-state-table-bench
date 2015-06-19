package org.midonet.benchmarks;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.inject.Injector;

import org.I0Itec.zkclient.ZkClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.midonet.midolman.layer3.Route;
import org.midonet.midolman.serialization.SerializationException;
import org.midonet.midolman.state.ArpCacheEntry;
import org.midonet.midolman.state.ReplicatedSet;
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
     * The maximum time during which a client waits for an update. Passed this time,
     * the client will consider the benchmark over.
     */
    private static long CLIENT_TIMEOUT = 120000;

    private int dataSize;
    private int writeCount;
    // Update rate in writes per second
    private int writeRate;

    // For each map/set version, we record a time.
    // For the updater, this is the time when the update was issued.
    // For the client, this is the reception time of the table version.
    private long[] versionTimestamps;

    // TO BE REMOVED
    private int mpiRank = 0;
    private int mpiSize = 1;

    public ChurnBench(Injector injector,
                      StorageType storageType, int size, int writeCount,
                      int writeRate, ZkClient zkClient) throws Exception {
        super(storageType, size, zkClient);
        this.dataSize = size;
        this.writeCount = writeCount;
        this.writeRate = writeRate;
        this.versionTimestamps = new long[writeCount];
    }

    private void arpBench(int opCount, boolean warmup) throws InterruptedException {
        // The process with rank 0 is the updater
        if (mpiRank == 0) {
            int sleepTime = 1000 / writeRate;
            for (int version = 0; version < opCount; version++) {
                if (!warmup)
                    versionTimestamps[version] = System.currentTimeMillis();

                // We encode the table version in the expiry field
                // of the arp entry.
                ArpCacheEntry arpEntry =
                    new ArpCacheEntry(MAC.random(), version /*expiry*/,
                                      0 /*stale*/, 0 /*lastArp*/);
                arpTable.put(DUMMY_IP, arpEntry);
                Thread.sleep(sleepTime);
            }

        // Otherwise, the process is a client. Only execute this block
        // for the actual benchmark, not the warmup phase.
        } else if (!warmup) {
            ReplicatedMapWatcher<IPv4Addr, ArpCacheEntry> arpWatcher =
                new ReplicatedMapWatcher<>();
            arpTable.addWatcher(arpWatcher);

            int version;

            do {
                arpWatcher.waitForResult(CLIENT_TIMEOUT);
                ArpCacheEntry arpEntry = arpTable.get(DUMMY_IP);
                version = (int) arpEntry.expiry;

                versionTimestamps[version] = System.currentTimeMillis();
            } while (version < (writeCount - 1));
        }
    }

    private void macBench(int opCount, boolean warmup) throws InterruptedException {
        // The process with rank 0 is the updater
        if (mpiRank == 0) {
            int sleepTime = 1000 / writeRate;
            for (int version = 0; version < opCount; version++) {
                if (!warmup)
                    versionTimestamps[version] = System.currentTimeMillis();

                // We encode the table version in least significant
                // bits of the UUID.
                macTable.put(DUMMY_MAC, new UUID(0, version));
                Thread.sleep(sleepTime);
            }

        // Otherwise, the process is a client. Only execute this block
        // for the actual benchmark, not the warmup phase.
        } else if (!warmup) {
            ReplicatedMapWatcher<MAC, UUID> macWatcher =
                new ReplicatedMapWatcher<>();
            macTable.addWatcher(macWatcher);

            int lastVersion = -1;
            boolean done = false;

            do {
                macWatcher.waitForResult(CLIENT_TIMEOUT);
                UUID uuid = macTable.get(DUMMY_MAC);
                int version = (int) uuid.getLeastSignificantBits();

                // If we get the same version, then we timed-out on the watcher
                // and thus the benchmark is over.
                if (version == lastVersion) {
                    done = true;
                } else {
                    versionTimestamps[version] = System.currentTimeMillis();
                    lastVersion = version;
                }
            } while (!done);
        }
    }

    private void routeBench(int opCount, boolean warmup) throws Exception {
        // The process with rank 0 is the updater
        if (mpiRank == 0) {
            int sleepTime = 1000 / writeRate;
            for (int version = 0; version < opCount; version++) {
                if (!warmup)
                    versionTimestamps[version] = System.currentTimeMillis();

                if (!warmup) {
                    // We encode the set version in the size of the route set.
                    // More specifically, the version is the set size minus
                    // the already populated routes.
                    routeSet.add(randomRoute());

                    Thread.sleep(sleepTime);
                } else {
                    // If we are doing the warmup, alternate between
                    // adding a route and removing one to keep the route set
                    // size identical. If opCount is odd, we omit the last
                    // addition.
                    if ((version < (opCount - 1)) || (opCount % 2 == 0)) {
                        if (rnd.nextInt(1) == 0) {
                            routeSet.add(randomRoute());
                        } else {
                            Route route = removeRndRoute();
                            routeSet.remove(route);
                        }
                    }
                }
            }

        // Otherwise, the process is a client. Only execute this block
        // for the actual benchmark, not the warmup phase.
        } else if (!warmup) {
            ReplicatedSetWatcher<Route> routeWatcher =
                new ReplicatedSetWatcher<>();
            routeSet.addWatcher(routeWatcher);

            int lastVersion = -1;
            boolean done = false;

            do {
                routeWatcher.waitForResult(CLIENT_TIMEOUT);
                Set<String> routes = routeSet.getStrings();

                // versions start out at zero hence the "-1"
                int version = routes.size() - dataSize - 1;
                // If we get the same version, then we timed-out on the watcher
                // and thus the benchmark is over.
                if (version == lastVersion) {
                    done = true;
                } else {
                    versionTimestamps[version] = System.currentTimeMillis();
                    lastVersion = version;
                }
            } while (!done);
        }
    }

    private void computeDistribLatency(Map<Integer, List<Long>> latencyMap) {
        for (int samples : latencyMap.keySet()) {
            results.put("Avg. Latency in ms for " + samples + " clients with " +
                latencyMap.get(samples).size() + " latencies is: " ,
                        StatUtils.mean(latencyMap.get(samples)));
        }
    }

    private long computeMaxTS(int version, long[] ts) {
        long max = 0;

        for (int client = 1; client < mpiSize; client++) {
            if (ts[client * writeCount + version] > max) {
                max = ts[client * writeCount + version];
            }
        }
        return max;
    }

    private void computeTotalTime(long[] ts) {
        long lastClientTS = computeMaxTS(writeCount - 1, ts);
        long totalTime = lastClientTS - ts[0];
        results.put("Total time for exp: ", new Long(totalTime));
    }

    private void collectResults() throws MPIException {
        List<Long> latencies = new LinkedList<>();
        Map<Integer, List<Long>> latencyMap = new HashMap<>();
        long[] results = null; //gather(versionTimestamps, mpiRoot);

        if (mpiRank == 0) {
            log.info("Collecting results for: {} with size: {} and rate: {}",
                     storageType, dataSize, writeRate);

            // Compute latencies for versions of the map/set
            // that have a timestamp for all clients.
            for (int version = 0; version < writeCount; version++) {
                long latency = 0;
                int samples = 0;

                // The updater timestamp is the timestamp of process with rank 0
                long updateTS = results[version];

                for (int process = 1; process < mpiSize; process++) {
                    long clientTS = results[(process * writeCount) + version];
                    if (clientTS != 0) {
                        latencies.add(clientTS - updateTS);
                        latency += (clientTS - updateTS);
                        samples++;
                    }
                    log.info("version: {} updateTS: {} client: {} ts: {}",
                             version, updateTS, process, clientTS);
                }

                if (samples > 0) {
                    if (!latencyMap.containsKey(samples))
                        latencyMap.put(samples, new LinkedList<Long>());
                    latencyMap.get(samples).add(latency / samples);
                }
            }
            computeStats(latencies);
            computeDistribLatency(latencyMap);
            computeTotalTime(results);
            printResults(log);
        }
    }

    private void warmup(int opCount) throws Exception {
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
            if (mpiRank == 0) {
                populateTable();
                warmup(WARMUP_OP_COUNT);
            }
        } catch (Exception e) {
            log.error(
                "Caught exception when populating/warming-up the table/set");
        }
        try {
            // Wait for sufficiently long so that clients timeout
            if (mpiRank == 0) {
                Thread.sleep(2 * CLIENT_TIMEOUT);
            }
            log.info("MPI rank: {}, waiting on barrier, mpi rank", mpiRank);
            // All processes (the updater and the clients) synchronize with this
            // barrier to start the benchmark approximately at the same time.
            //barrier();
            log.info("MPI rank: {}, executing benchmark", mpiRank);

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

            for (int version = 0; version < writeCount; version++) {
                log.info("Process with rank {} version: {} ts: {}", mpiRank,
                         version, versionTimestamps[version]);
            }

            collectResults();
        } catch(Exception e) {
            log.error("Exception caught when executing Churn benchmark", e);
        }
    }

    public static void main(String[] args) {
        if (args.length == 4) {
            String configFile = System.getProperty("midobench.config");
            StorageType storageType = StorageType.valueOf(args[0]);
            int size = Integer.parseInt(args[1]);
            int writeCount = Integer.parseInt(args[2]);
            int writeRate = Integer.parseInt(args[3]);

            try {
                MPI.Init(args);
                Injector injector = MapSetBenchmark.createInjector(configFile);
                String mpiHosts = getMpiHosts(configFile);
                ChurnBench churnBench = new ChurnBench(injector,
                                                       storageType, size,
                                                       writeCount, writeRate,
                                                       null /* zkClient */);
                churnBench.run();
            } catch(Exception e) {
                log.warn("Unable to initialize churn benchmark", e);
            }
        } else {
            log.error("Please specify the data structure "
                      + "type (MAC_TABLE, ARP_TABLE, or ROUTING_TABLE), the "
                      + "size of the data structure, the number of writes "
                      + "to perform, and the update rate");
        }
    }
}
