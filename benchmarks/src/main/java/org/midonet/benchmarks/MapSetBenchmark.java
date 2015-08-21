package org.midonet.benchmarks;

import com.google.inject.*;
import com.google.inject.name.Names;
import com.typesafe.config.Config;
import org.I0Itec.zkclient.ZkClient;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.midonet.cluster.data.storage.ArpMergedMap;
import org.midonet.cluster.data.storage.MergedMap;
import org.midonet.cluster.storage.MidonetBackendConfig;
import org.midonet.cluster.storage.MidonetBackendModule;
import org.midonet.conf.MidoNodeConfigurator;
import org.midonet.midolman.cluster.LegacyClusterModule;
import org.midonet.midolman.cluster.serialization.SerializationModule;
import org.midonet.midolman.cluster.zookeeper.DirectoryProvider;
import org.midonet.midolman.cluster.zookeeper.ZkConnectionProvider;
import org.midonet.midolman.guice.config.MidolmanConfigModule;
import org.midonet.midolman.layer3.Route;
import org.midonet.midolman.serialization.SerializationException;
import org.midonet.midolman.serialization.Serializer;
import org.midonet.midolman.state.*;
import org.midonet.packets.IPv4Addr;
import org.midonet.packets.MAC;
import org.midonet.util.eventloop.Reactor;
import org.midonet.util.eventloop.TryCatchReactor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.observers.TestObserver;

import java.io.File;
import java.util.*;

/**
 * This is the super-class for various benchmarks on replicated maps/sets.
 */
public abstract class MapSetBenchmark { // extends MPIBenchApp {

    private static final Logger log =
            LoggerFactory.getLogger(MapSetBenchmark.class);

    // When populating a table, we wait until dataSize * FILL_RATIO
    // entries have been inserted in the table before proceeding to the
    // warmup phase.
    private static final float FILL_RATIO = 0.7f;
    protected static int WARMUP_OP_COUNT = 300;
    protected Map<String, Object> results;

    /* Maximum time to wait for a merged map population */
    protected static final long POPULATE_TIMEOUT = 60000;

    public enum StorageType {
        MAC_TABLE,
        ARP_TABLE,
        ROUTING_TABLE,
        ARP_MERGED_MAP
    }

    protected StorageType storageType;
    protected int dataSize;

    protected Serializer serializer;
    protected ArpTable arpTable;
    protected MergedMap<IPv4Addr, ArpCacheEntry> arpMergedMap;
    protected Map<Integer, IPv4Addr> arpTableKeys;
    protected MacPortMap macTable;
    protected Map<Integer, MAC> macTableKeys;
    protected ReplicatedRouteSet routeSet;
    protected ArrayList<Route> routes;
    protected Random rnd;

    public MapSetBenchmark(StorageType storageType, int dataSize,
                           ZkClient zkClient)
            throws Exception {

        //super(MPI.COMM_WORLD.getSize(), MPI.COMM_WORLD.getRank(),
        //      mpiHosts);

        this.results = new HashMap<>();
        this.storageType = storageType;
        this.dataSize = dataSize;
        this.rnd = new Random();
        //this.serializer = injector.getInstance(Serializer.class);
        //ZkConnection zkConn = injector.getInstance(ZkConnection.class);
        //MidonetBackendConfig config =
        //   injector.getInstance(MidonetBackendConfig.class);
        //ZkDirectory zkDir =
        //    new ZkDirectory(zkConn, config.rootKey() + "/maps-sets",
        //null /* ACL */, new TryCatchReactor("Zookeeper", 1));

        //if (isMpiRoot()) {
        //    prepareZkPaths(zkDir, zkConn, config.rootKey(), storageType);
        //}

        switch (storageType) {
            case MAC_TABLE:
                //initMacTable(zkDir);
                break;
            case ARP_TABLE:
                //initArpTable(zkDir);
                break;
            case ROUTING_TABLE:
                //initRouteSet(zkDir);
                break;
            case ARP_MERGED_MAP:
                initArpMergedMap(zkClient);
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

        protected void waitForResult(long timeout) throws InterruptedException {
            synchronized (this) {
                while (!updateRcvd) {
                    wait(timeout);

                    if (timeout > 0) {
                        break;
                    }
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

        protected void waitForResult(long timeout) throws InterruptedException {
            synchronized (this) {
                while (!updateRcvd) {
                    wait(timeout);
                }
                updateRcvd = false;
            }
        }
    }

    //TODO: Do this properly
    protected static String getMpiHosts(String configFile) {
        Config config =
                MidoNodeConfigurator.forAgents(configFile).localOnlyConfig();
        return config.getString("mpi.mpi_hosts");
    }

    //TODO: Do this properly
    protected static int getThreadsPerHost(String configFile) {
        Config config =
                MidoNodeConfigurator.forAgents(configFile).localOnlyConfig();
        return config.getInt("benchmarks.threads_per_host");
    }

    protected static Injector createInjector(final String configFile) {
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

    private static void prepareZkPaths(ZkDirectory zkDir, ZkConnection zkConn,
                                       String basePath,
                                       StorageType storageType) {
        try {
            // Create the necessary paths
            String benchPath = zkDir.getPath();
            String[] paths = benchPath.split("/");
            StringBuffer absPath = new StringBuffer();
            ZooKeeper zk = zkConn.getZooKeeper();

            log.info("Creating ZK base path {}", benchPath);
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
        } catch (Exception e) {
            log.error("Exception was caught when creating the Zookeeper"
                    + " base path", e);
        }

        Set<String> children = new HashSet<>();
        try {
            children =
                    zkDir.getChildren("", new Directory.DefaultTypedWatcher());
        } catch (Exception e) {
            log.error("Impossible to obtain map/set entries", e);
        }

        log.info("Deleting left-over children, count {}", children.size());
        // Delete any left-over children
        for (String child : children) {
            // Children are ephemeral nodes so it can happen that some
            // get deleted in the meantime. We just ignore such cases.
            try {
                zkDir.delete("/" + child);
            } catch (Exception e) {
            }
        }

        try {
            if (storageType == StorageType.ROUTING_TABLE) {
                zkConn.getZooKeeper()
                        .create(basePath + "/write_version", "-1".getBytes(),
                                ZooDefs.Ids.OPEN_ACL_UNSAFE,
                                CreateMode.PERSISTENT);
            }
        } catch (Exception e) {
            log.error("Impossible to create the version directory "
                    + "needed for routes", e);
        }
        log.info("***ZK maps/sets path: {}", zkDir.getPath());
    }

    private void initArpTable(ZkDirectory zkDir) {
        arpTable = new ArpTable(zkDir);
        arpTable.start();
        arpTableKeys = new HashMap<>(dataSize);
    }

    protected void populateTable() throws InterruptedException,
            SerializationException {
        switch (storageType) {
            case ARP_TABLE:
                populateArpTable();
                break;
            case ARP_MERGED_MAP:
                populateArpMergedMap();
                break;
            case MAC_TABLE:
                populateMacTable();
                break;
            case ROUTING_TABLE:
                populateRouteSet();
                break;
        }
    }

    private void waitForCompleteArpTable(
            ReplicatedMapWatcher<IPv4Addr, ArpCacheEntry> arpWatcher)
            throws InterruptedException {
        int size;
        do {
            arpWatcher.waitForResult(0 /* wait until notified */);
            size = arpTable.getMap().size();
            // We don't necessarily receive all updates so wait only until 90%
            // of the expected size is reached.
        } while (size < (FILL_RATIO * dataSize));
    }

    protected void populateArpMergedMap() {
        log.info("Populating ARP merged map with {} entries", dataSize);
        long start = System.currentTimeMillis();

        TestObserver obs = ArpMergedMap.arpMapObserver(arpMergedMap);
        for (int i = 0; i < dataSize; i++) {
            IPv4Addr ip = randomIP();
            arpMergedMap.putOpinion(ip, randomArpEntry());
            arpTableKeys.put(i, ip);
        }
        ArpMergedMap.awaitForObserverEvents(obs, dataSize, POPULATE_TIMEOUT);
        long end = System.currentTimeMillis();
        long duration = end - start;

        if (arpMergedMap.size() == dataSize) {
            log.info("Population completed in {} ms", duration);
        } else {
            log.info("Only populated arp merged map with: {} entries in "
                    + "{} ms", arpMergedMap.size(), duration);
        }
    }

    protected void populateArpTable() throws InterruptedException {
        ReplicatedMapWatcher<IPv4Addr, ArpCacheEntry> arpWatcher =
                new ReplicatedMapWatcher<>();
        arpTable.addWatcher(arpWatcher);

        log.info("Populating ARP Table with {} entries", dataSize);
        long start = System.currentTimeMillis();
        for (int i = 0; i < dataSize; i++) {
            IPv4Addr ip = randomIP();
            arpTable.put(ip, randomArpEntry());
            arpTableKeys.put(i, ip);
        }
        waitForCompleteArpTable(arpWatcher);
        arpTable.removeWatcher(arpWatcher);
        long end = System.currentTimeMillis();
        log.info("Population completed in {} ms", (end - start));
    }

    private void initMacTable(ZkDirectory zkDir) {
        macTable = new MacPortMap(zkDir, true /* ephemeral */);
        macTable.start();
        macTableKeys = new HashMap<>(dataSize);
    }

    private void waitForCompleteMacTable(
            ReplicatedMapWatcher<MAC, UUID> macWatcher) throws InterruptedException {
        int size;
        do {
            macWatcher.waitForResult(0 /* wait until notified */);
            size = macTable.getMap().size();
            // We don't necessarily receive all updates so wait only until 90%
            // of the expected size is reached.
        } while (size < (FILL_RATIO * dataSize));
    }

    protected void populateMacTable() throws InterruptedException {
        ReplicatedMapWatcher<MAC, UUID> macWatcher =
                new ReplicatedMapWatcher<>();
        macTable.addWatcher(macWatcher);

        log.info("Populating MAC Table with {} entries", dataSize);
        long start = System.currentTimeMillis();
        for (int i = 0; i < dataSize; i++) {
            MAC mac = MAC.random();
            macTable.put(mac, UUID.randomUUID());
            macTableKeys.put(i, mac);
        }
        waitForCompleteMacTable(macWatcher);
        macTable.removeWatcher(macWatcher);
        long end = System.currentTimeMillis();
        log.info("Population completed in {} ms", (end - start));
    }

    private void initArpMergedMap(ZkClient zkClient) {
        arpTableKeys = new HashMap<>(dataSize);
        arpMergedMap = ArpMergedMap.newArpMap("arp", "owner", zkClient);
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

    private void waitForCompleteRouteSet(ReplicatedSetWatcher routeWatcher)
            throws InterruptedException {
        int size;
        do {
            routeWatcher.waitForResult(0 /* wait until notified */);
            size = routeSet.getStrings().size();
            // We don't necessarily receive all updates so wait only until 90%
            // of the expected size is reached.
        } while (size < (FILL_RATIO * dataSize));
    }

    protected void populateRouteSet()
            throws InterruptedException, SerializationException {

        ReplicatedSetWatcher routeWatcher = new ReplicatedSetWatcher<>();
        routeSet.addWatcher(routeWatcher);

        log.info("Populating Route Set with {} entries", dataSize);
        long start = System.currentTimeMillis();
        for (int i = 0; i < dataSize; i++) {
            Route route = randomRoute();
            routes.add(route);
            routeSet.add(route);
        }
        waitForCompleteRouteSet(routeWatcher);
        routeSet.removeWatcher(routeWatcher);
        long end = System.currentTimeMillis();
        log.info("Population completed in {} ms", (end - start));
    }

    protected IPv4Addr randomExistingIP() {
        return arpTableKeys.get(rnd.nextInt(dataSize));
    }

    protected MAC randomExistingMAC() {
        return macTableKeys.get(rnd.nextInt(dataSize));
    }

    protected Route removeRndRoute() {
        int index = rnd.nextInt(routes.size());
        return routes.remove(index);
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

    protected void computeStats(List<Long> latencies) {
        results.put("Number of latencies: ", latencies.size());
        results.put("Avg. Latency in ms", StatUtils.mean(latencies) / 1000000d);
        results.put("Std. deviation of latency in ms",
                StatUtils.standardDeviation(latencies) / 1000000d);
        results.put("90% percentile of latency in ms",
                StatUtils.percentile(latencies, 0.9d) / 1000000l);
    }

    protected void printResults(Logger log) {
        System.out.println("*** Results for: " + storageType + " of size: " +
                dataSize + " :");
        for (String key : results.keySet())
            System.out.println("\t" + key + " " + results.get(key));
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

    public static class ZookeeperReactorProvider implements Provider<Reactor> {
        private final static int nOfThreads =
                getThreadsPerHost(System.getProperty("midobench.config"));

        public ZookeeperReactorProvider() {
        }

        public Reactor get() {
            return new TryCatchReactor("zookeeper", Integer.valueOf(nOfThreads));
        }
    }

    public static class ZookeeperConnectionModule extends PrivateModule {
        private final Class<? extends ZkConnectionAwareWatcher> connWatcherImpl;

        public ZookeeperConnectionModule(Class<? extends ZkConnectionAwareWatcher> connWatcherImpl) {
            this.connWatcherImpl = connWatcherImpl;
        }

        protected void configure() {
            this.binder().requireExplicitBindings();
            this.requireBinding(MidonetBackendConfig.class);
            this.bindZookeeperConnection();
            this.bindDirectory();
            this.bindReactor();
            this.expose(Key.get(Reactor.class, Names.named("directoryReactor")));
            this.expose(Directory.class);
            this.bindZkConnectionWatcher();
        }

        protected void bindZkConnectionWatcher() {
            this.bind(ZkConnectionAwareWatcher.class).to(this.connWatcherImpl).asEagerSingleton();
            this.expose(ZkConnectionAwareWatcher.class);
        }

        protected void bindDirectory() {
            this.bind(Directory.class).toProvider(DirectoryProvider.class).asEagerSingleton();
        }

        protected void bindZookeeperConnection() {
            this.bind(ZkConnection.class).toProvider(ZkConnectionProvider.class).asEagerSingleton();
            this.expose(ZkConnection.class);
        }

        protected void bindReactor() {
            this.bind(Reactor.class).annotatedWith(Names.named("directoryReactor"))
                    .toProvider(ZookeeperReactorProvider.class).asEagerSingleton();
        }
    }

    /**
     * Runs the benchmark. This is implemented in the sub-classes.
     */
    abstract protected void run();
}
