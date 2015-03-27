package org.midonet.benchmarks;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;

import org.apache.zookeeper.KeeperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.midonet.cluster.storage.MidonetBackendModule;
import org.midonet.midolman.cluster.LegacyClusterModule;
import org.midonet.midolman.cluster.serialization.SerializationModule;
import org.midonet.midolman.cluster.zookeeper.ZookeeperConnectionModule;
import org.midonet.midolman.config.MidolmanConfig;
import org.midonet.midolman.state.ArpCacheEntry;
import org.midonet.midolman.state.ArpTable;
import org.midonet.midolman.state.ReplicatedMap;
import org.midonet.midolman.state.ZkConnection;
import org.midonet.midolman.state.ZkDirectory;
import org.midonet.midolman.state.ZookeeperConnectionWatcher;
import org.midonet.packets.IPv4Addr;
import org.midonet.packets.MAC;
import org.midonet.util.eventloop.TryCatchReactor;

/**
 * This is the super-class for various benchmarks on replicated maps/sets.
 */
public abstract class MapSetBenchmark {

    private static final Logger log =
        LoggerFactory.getLogger(LatencyBench.class);

    protected Map<String, Object> results;

    public enum StorageType {
        MAC_TABLE,
        ARP_TABLE,
        ROUTING_TABLE
    }
    protected StorageType storageType;
    protected ArpTable arpTable;
    protected Map<Integer, IPv4Addr> arpTableKeys;
    protected int dataSize;

    protected Random rnd;

    protected static class ReplicatedMapWatcher<K, V>
        implements ReplicatedMap.Watcher<K, V> {

        long rcvTime;
        private CountDownLatch latch = new CountDownLatch(1);

        public void processChange(K var1, V var2, V var3) {
            rcvTime = System.currentTimeMillis();
            latch.countDown();
        }
        protected void resetLatch() {
            latch = new CountDownLatch(1);
        }
        protected void waitForResult() throws InterruptedException {
            latch.await();
        }
    }

    protected ReplicatedMapWatcher<IPv4Addr, ArpCacheEntry> arpWatcher;

    private static Injector createInjector(final String configFilePath) {

        AbstractModule benchModule = new AbstractModule() {
            @Override
            protected void configure() {
                install(new ZookeeperConnectionModule(
                    ZookeeperConnectionWatcher.class));
                // This installs a default configuration where ZK is expected
                // to run locally.
                // TODO: Allow to change the ZK config.
                install(MidonetBackendModule.apply());
                install(new LegacyClusterModule());
                install(new SerializationModule());
            }
        };
        return Guice.createInjector(benchModule);
    }

    private void initArpTable(ZkDirectory zkDir, int size)
        throws InterruptedException, KeeperException {

        // Ensure that the base directory exists.
        zkDir.ensureHas("", new byte[0]);
        arpTable = new ArpTable(zkDir);
        arpTable.start();
        arpTableKeys = new HashMap<>(size);
        arpWatcher = new ReplicatedMapWatcher<>();
    }

    protected void populateArpTable(ArpTable arpTable)
        throws InterruptedException{

        log.info("Populating ARP Table with {} entries", dataSize);
        long start = System.currentTimeMillis();
        for (int i=0; i < dataSize; i++) {
            arpTable.addWatcher(arpWatcher);
            IPv4Addr ip = randomIP();
            arpTable.put(ip, randomArpEntry());
            arpTableKeys.put(i, ip);
            arpWatcher.waitForResult();
            arpWatcher.resetLatch();
        }
        long end = System.currentTimeMillis();
        log.info("Population completed in {} ms", (end-start));
    }

    public MapSetBenchmark(StorageType storageType, String configFile,
                           int dataSize)
        throws InterruptedException, KeeperException {

        this.results = new HashMap<>();
        this.storageType = storageType;
        this.dataSize = dataSize;
        this.rnd = new Random();
        ensureConfigFileExists(configFile);
        Injector injector = createInjector(configFile);
        ZkConnection zkConn = injector.getInstance(ZkConnection.class);

        // TODO: Obtain the zk root path from the config
        // MidolmanConfig conf = injector.getInstance(MidolmanConfig.class);
        //((MidonetBackendConfig) conf.zookeeper()).root_key...
        String zkBasePath = "/mido-bench";
        ZkDirectory zkDir =
            new ZkDirectory(zkConn, zkBasePath, null /* ACL */,
                            new TryCatchReactor("Zookeeper", 1));
        switch (storageType) {
            case MAC_TABLE:
                break;

            case ARP_TABLE:
                initArpTable(zkDir, dataSize);
                break;

            case ROUTING_TABLE:
                break;
        }
    }

    /**
     * Checks whether the configuration file exists. If not, an
     * IllegalArgumentException is thrown.
     *
     * @throws IllegalArgumentException when the configuration file does not
     *                                  exist.
     */
    protected void ensureConfigFileExists(String configFile) {
        File config = new File(configFile);
        if (!config.exists())
            throw new IllegalArgumentException("Configuration file: " +
                                               configFile + " does not exist");
    }

    protected void printResults(Logger log) {
        log.info("*** Benchmark is over, results:");
        for (String key: results.keySet())
            log.info("\t {}: {}", key, results.get(key));
    }

    private IPv4Addr randomIP() {
        return IPv4Addr.random();
    }

    protected ArpCacheEntry randomArpEntry() {
        MAC mac = MAC.random();
        return new ArpCacheEntry(mac, 0 /*expiry*/, 0 /*stale*/, 0 /*lastArp*/);
    }

    /**
     * Runs the benchmark. This is implemented in the sub-classes.
     */
    abstract protected void run() throws InterruptedException;
}
