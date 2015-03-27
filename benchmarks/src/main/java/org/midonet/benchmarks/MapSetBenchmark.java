package org.midonet.benchmarks;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.midonet.cluster.storage.MidonetBackendModule;
import org.midonet.config.ConfigProvider;
import org.midonet.midolman.cluster.LegacyClusterModule;
import org.midonet.midolman.cluster.config.ConfigProviderModule;
import org.midonet.midolman.cluster.serialization.SerializationModule;
import org.midonet.midolman.cluster.zookeeper.ZookeeperConnectionModule;
import org.midonet.midolman.state.ArpTable;
import org.midonet.midolman.state.ZkConnection;
import org.midonet.midolman.state.ZkDirectory;
import org.midonet.midolman.state.ZookeeperConnectionWatcher;
import org.midonet.util.eventloop.TryCatchReactor;

/**
 * This is the container of the code of the different benchmarking steps related
 * to replicated maps/sets.
 * TO BE COMPLETED
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

    // TODO: add this in a configuration file
    private String ZKBasePath = "/bench";

    private static Injector createInjector(String configFilePath) {

        AbstractModule benchModule = new AbstractModule() {
            @Override
            protected void configure() {
                requireBinding(ConfigProvider.class);
                install(new ZookeeperConnectionModule(
                    ZookeeperConnectionWatcher.class));
                install(new MidonetBackendModule());
                install(new LegacyClusterModule());
                install(new SerializationModule());
            }
        };
        return Guice.createInjector(
            new ConfigProviderModule(configFilePath), benchModule);
    }

    public MapSetBenchmark(StorageType storageType, String configFile) {
        this.results = new HashMap<>();
        this.storageType = storageType;
        ensureConfigFileExists(configFile);
        Injector injector = createInjector(configFile);
        ZkConnection zkConn = injector.getInstance(ZkConnection.class);

        switch (storageType) {
            case MAC_TABLE:
                break;

            case ARP_TABLE:
                ZkDirectory zkDir =
                    new ZkDirectory(zkConn, ZKBasePath, null /* ACL */,
                                    new TryCatchReactor("Zookeeper", 1));
                arpTable = new ArpTable(injector.getInstance(ZkDirectory.class));
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

    /**
     * Runs the benchmark. This is implemented in the sub-classes.
     */
    abstract protected void run();
}
