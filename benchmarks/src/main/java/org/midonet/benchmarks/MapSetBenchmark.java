package org.midonet.benchmarks;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.typesafe.config.Config;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.midonet.cluster.storage.MidonetBackendConfig;
import org.midonet.cluster.storage.MidonetBackendModule;
import org.midonet.conf.MidoNodeConfigurator;
import org.midonet.midolman.cluster.LegacyClusterModule;
import org.midonet.midolman.cluster.serialization.SerializationModule;
import org.midonet.midolman.cluster.zookeeper.ZookeeperConnectionModule;
import org.midonet.midolman.guice.config.MidolmanConfigModule;
import org.midonet.midolman.layer3.Route;
import org.midonet.midolman.serialization.SerializationException;
import org.midonet.midolman.serialization.Serializer;
import org.midonet.midolman.state.ArpCacheEntry;
import org.midonet.midolman.state.ArpTable;
import org.midonet.midolman.state.Directory;
import org.midonet.midolman.state.DirectoryCallback;
import org.midonet.midolman.state.MacPortMap;
import org.midonet.midolman.state.ReplicatedMap;
import org.midonet.midolman.state.ReplicatedSet;
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
    protected int dataSize;

    protected Serializer serializer;
    protected ArpTable arpTable;
    protected Map<Integer, IPv4Addr> arpTableKeys;
    protected MacPortMap macTable;
    protected Map<Integer, MAC> macTableKeys;
    protected ReplicatedRouteSet routeSet;
    protected ArrayList<Route> routes;
    protected Random rnd;

    public MapSetBenchmark(StorageType storageType, String configFile,
                           int dataSize)
        throws InterruptedException, KeeperException {

        this.results = new HashMap<>();
        this.storageType = storageType;
        this.dataSize = dataSize;
        this.rnd = new Random();
        ensureConfigFileExists(configFile);
        Injector injector = createInjector(configFile);
        this.serializer = injector.getInstance(Serializer.class);
        ZkConnection zkConn = injector.getInstance(ZkConnection.class);
        MidonetBackendConfig config =
            injector.getInstance(MidonetBackendConfig.class);
        ZkDirectory zkDir =
            new ZkDirectory(zkConn, config.rootKey() + "/maps-sets",
                            null /* ACL */, new TryCatchReactor("Zookeeper", 1));
        prepareZkPaths(zkDir, zkConn);

        switch (storageType) {
            case MAC_TABLE:
                initMacTable(zkDir);
                break;
            case ARP_TABLE:
                initArpTable(zkDir);
                break;
            case ROUTING_TABLE:
                initRouteSet(zkDir);
                break;
        }
    }

    private class RouteEncoder {

        protected String encode(Route rt) {
            //TODO(dmd): this is slightly ghetto
            try {
                return new String(serializer.serialize(rt));
            } catch (SerializationException e) {
                log.error("Could not serialize route {}, exception: {}", rt, e);
                return null;
            }
        }

        protected Route decode(String str) {
            try {
                return serializer.deserialize(str.getBytes(), Route.class);
            } catch (SerializationException e) {
                log.error("Could not deserialize route {}, exception: {}",
                          str, e);
                return null;
            }
        }
    }

    protected class ReplicatedRouteSet extends ReplicatedSet<Route> {
        RouteEncoder encoder = new RouteEncoder();
        Directory dir;

        public ReplicatedRouteSet(Directory d, CreateMode mode) {
            super(d, mode);
            dir = d;
        }

        class DeleteCallback implements DirectoryCallback.Void {
            private Route item;

            private DeleteCallback(Route item) {
                this.item = item;
            }

            @Override
            public void onSuccess(java.lang.Void data) {
                log.debug("ReplicatedSet delete {} succeeded", item);
            }

            @Override
            public void onTimeout() {
                log.error("ReplicatedSet delete {} timed out.", item);
            }

            @Override
            public void onError(KeeperException e) {
                log.error("ReplicatedSet Delete {} failed", item, e);
            }
        }

        @Override
        protected String encode(Route item) {
            return encoder.encode(item);
        }

        @Override
        protected Route decode(String str) {
            return encoder.decode(str);
        }

        /**
         * We override this method because the one in ReplicatedSet is buggy:
         * it does not convert the relative path into an absolute one.
         */
        @Override
        public void remove(Route route) throws SerializationException {
            String absPath = dir.getPath() + "/" + this.encode(route);
            this.dir.asyncDelete(absPath, new DeleteCallback(route));
        }
    }

    protected static class ReplicatedMapWatcher<K, V>
        implements ReplicatedMap.Watcher<K, V> {

        long rcvTime;
        private boolean updateRcvd = false;

        public void processChange(K var1, V var2, V var3) {
            rcvTime = System.currentTimeMillis();
            synchronized (this) {
                updateRcvd = true;
                notify();
            }
        }
        protected void waitForResult() throws InterruptedException {
            synchronized (this) {
                while (!updateRcvd) {
                    wait();
                }
                updateRcvd = false;
            }
        }
    }

    protected static class ReplicatedSetWatcher<Route>
        implements ReplicatedSet.Watcher<Route> {

        long rcvTime;
        private boolean updateRcvd = false;

        public void process(Collection<Route> added, Collection<Route> removed) {
            rcvTime = System.currentTimeMillis();
            synchronized (this) {
                updateRcvd = true;
                notify();
            }
        }

        protected void waitForResult() throws InterruptedException {
            synchronized (this) {
                while (!updateRcvd) {
                    wait();
                }
                updateRcvd = false;
            }
        }
    }

    private static Injector createInjector(final String configFile) {
        AbstractModule benchModule = new AbstractModule() {
            @Override
            protected void configure() {
                Config config =
                    MidoNodeConfigurator.forAgents(configFile).localOnlyConfig();
                install(new MidolmanConfigModule(config));
                install(new MidonetBackendModule(config));
                install(new ZookeeperConnectionModule(
                    ZookeeperConnectionWatcher.class));
                install(new LegacyClusterModule());
                install(new SerializationModule());
            }
        };
        return Guice.createInjector(benchModule);
    }

    private void prepareZkPaths(ZkDirectory zkDir, ZkConnection zkConn) {

        try {
            // Create the necessary paths
            String basePath = zkDir.getPath();
            String[] paths = basePath.split("/");
            StringBuffer absPath = new StringBuffer();
            ZooKeeper zk = zkConn.getZooKeeper();

            for (String path : paths) {
                if (!path.isEmpty()) {
                    absPath.append("/" + path);

                    if (zk.exists(absPath.toString(), false /* watch */) == null) {
                        zkConn.getZooKeeper()
                            .create(absPath.toString(), new byte[0],
                                    ZooDefs.Ids.OPEN_ACL_UNSAFE,
                                    CreateMode.PERSISTENT);
                    }
                }
            }

            // Delete any left-over children
            Set<String> children =
                zkDir.getChildren("", new Directory.DefaultTypedWatcher());
            for (String child: children) {
                zkDir.delete("/" + child);
            }
        } catch (Exception e) {
            log.error("Exception was caught when initializing the Zookeeper"
                      + " directory", e);
        }
        log.info("***ZK maps/sets path: {}", zkDir.getPath());
    }

    private void initArpTable(ZkDirectory zkDir) {
        arpTable = new ArpTable(zkDir);
        arpTable.start();
        arpTableKeys = new HashMap<>(dataSize);
    }

    protected void populateArpTable() throws InterruptedException {
        ReplicatedMapWatcher<IPv4Addr, ArpCacheEntry> arpWatcher =
            new ReplicatedMapWatcher<>();
        arpTable.addWatcher(arpWatcher);

        log.info("Populating ARP Table with {} entries", dataSize);
        long start = System.currentTimeMillis();
        for (int i=0; i < dataSize; i++) {
            IPv4Addr ip = randomIP();
            arpTable.put(ip, randomArpEntry());
            arpTableKeys.put(i, ip);
            arpWatcher.waitForResult();
        }
        arpTable.removeWatcher(arpWatcher);
        long end = System.currentTimeMillis();
        log.info("Population completed in {} ms", (end-start));
    }

    private void initMacTable(ZkDirectory zkDir) {
        macTable = new MacPortMap(zkDir, true /* ephemeral */);
        macTable.start();
        macTableKeys = new HashMap<>(dataSize);
    }

    protected void populateMacTable() throws InterruptedException {
        ReplicatedMapWatcher<MAC, UUID> macWatcher =
            new ReplicatedMapWatcher<>();
        macTable.addWatcher(macWatcher);

        log.info("Populating MAC Table with {} entries", dataSize);
        long start = System.currentTimeMillis();
        for (int i=0; i < dataSize; i++) {
            MAC mac = MAC.random();
            macTable.put(mac, UUID.randomUUID());
            macTableKeys.put(i, mac);
            macWatcher.waitForResult();
        }
        macTable.removeWatcher(macWatcher);
        long end = System.currentTimeMillis();
        log.info("Population completed in {} ms", (end-start));
    }

    private void initRouteSet(ZkDirectory zkDir) {
        try {
            zkDir.ensureHas("/write_version", new byte[0]);
            zkDir.update("/write_version", "bench_version".getBytes(), -1);
        } catch (Exception e) {
            log.error("Unable to update the route set version", e);
        }
        routeSet = new ReplicatedRouteSet(zkDir, CreateMode.EPHEMERAL);
        routeSet.start();
        routes = new ArrayList<>(dataSize);
    }

    protected void populateRouteSet()
        throws InterruptedException, SerializationException {

        ReplicatedSetWatcher routeWatcher = new ReplicatedSetWatcher<>();
        routeSet.addWatcher(routeWatcher);

        log.info("Populating Route Set with {} entries", dataSize);
        long start = System.currentTimeMillis();
        for (int i=0; i < dataSize; i++) {
            Route route = randomRoute();
            routes.add(route);
            routeSet.add(route);
            routeWatcher.waitForResult();
        }
        routeSet.removeWatcher(routeWatcher);
        long end = System.currentTimeMillis();
        log.info("Population completed in {} ms", (end-start));
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

    protected Route randomRoute() {
        int srcAddr = IPv4Addr.random().toInt();
        int dstAddr = IPv4Addr.random().toInt();
        int nextHopGtw = 0; //IPv4Addr.random().toInt();

        return new Route(srcAddr, 24 /* srcNetLength */, dstAddr,
                         24 /* destNetLength */, Route.NextHop.PORT,
                         UUID.randomUUID() /* port */, nextHopGtw, 1 /* weight */,
                         "" /* attribue */, UUID.randomUUID() /* routerId */);
    }

    /**
     * Runs the benchmark. This is implemented in the sub-classes.
     */
    abstract protected void run();
}
