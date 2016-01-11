package org.midonet.benchmarks;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import mpi.MPI;
import mpi.MPIException;
import org.midonet.benchmarks.latencyNodes.*;
import org.midonet.cluster.data.storage.ArpMergedMap;
import org.midonet.cluster.data.storage.MergedMap;
import org.midonet.cluster.data.storage.MergedMapBus;
import org.midonet.midolman.state.ArpCacheEntry;
import org.midonet.packets.IPv4Addr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.Tuple2;

import java.io.File;
import java.util.List;
import java.util.Random;
import java.util.Scanner;

/**
 * The MergedMapTestBench is for testing the latency/throughput of the kafka cluster. This test bench
 * creates multiple maps with a single writer node per map and one or more nodes receiving updates
 * for that map.
 *
 * An example of this with 2 maps and 3 readers per map
 *
 *  Writer          Writer
 *    |               |
 *   Map             Map
 * /  |  \         /  |  \
 * R  R  R         R  R  R
 *
 */
public class MergedMapTestBench extends TestBench {

    int worldSize;
    int worldRank;
    int numberOfMaps;
    int writeRate;
    int writeInterval;
    int warmupWrites;
    int benchmarkWrites;
    int readersPerMap;
    int tableSize = 1000;
    String mapBaseName;

    /**
     *
     * @param worldSize Information given by MPI
     * @param worldRank Information given by MPI
     * @param numberOfMaps
     * @param writeRate Number of map entries per second
     * @param writeInterval Interval in which to perform the writes in milliseconds
     * @param readersPerMap
     * @throws NotEnoughNodesAvailableException
     */
    public MergedMapTestBench(
            int worldSize,
            int worldRank,
            int numberOfMaps,
            int writeRate,
            int writeInterval,
            int warmupWrites,
            int benchmarkWrites,
            int readersPerMap,
            String mapBaseName,
            Bookkeeper bookkeeper
    ) {
        super(worldSize, worldRank, "");

        this.worldSize = worldSize;
        this.worldRank = worldRank;
        this.numberOfMaps = numberOfMaps;
        this.writeRate = writeRate;
        this.writeInterval = writeInterval;
        this.warmupWrites = warmupWrites;
        this.benchmarkWrites = benchmarkWrites;
        this.readersPerMap = readersPerMap;
        this.mapBaseName = mapBaseName;
        this.bookkeeper = bookkeeper;
    }

    public MergedMapTestBench(
            int worldSize,
            int worldRank
    ) {
        super(worldSize, worldRank, "");

        this.worldSize = worldSize;
        this.worldRank = worldRank;
    }

    public void configureWithConfig(Config config) {
        this.numberOfMaps = config.getInt("numberOfMaps");
        this.writeRate = config.getInt("writeRate");
        this.writeInterval = config.getInt("writeInterval") * 1000;
        this.warmupWrites = config.getInt("numberOfWarmupWrites");
        this.benchmarkWrites = config.getInt("benchMarkWrites");
        this.readersPerMap = config.getInt("readersPerMap");
        this.mapBaseName = config.getString("mapName");
        this.tableSize = config.getInt("tableSize");
    }

    public void updateConfigurationWithConfig(Config config) {
        if (config.hasPath("numberOfMaps")) {
            this.numberOfMaps = config.getInt("numberOfMaps");
        }
        if (config.hasPath("writeRate")) {
            this.writeRate = config.getInt("writeRate");
        }
        if (config.hasPath("writeInterval")) {
            this.writeInterval = config.getInt("writeInterval") * 1000;
        }
        if (config.hasPath("numberOfWarmupWrites")) {
            this.warmupWrites = config.getInt("numberOfWarmupWrites");
        }
        if (config.hasPath("benchMarkWrites")) {
            this.benchmarkWrites = config.getInt("benchMarkWrites");
        }
        if (config.hasPath("readersPerMap")) {
            this.readersPerMap = config.getInt("readersPerMap");
        }
        if (config.hasPath("mapName")) {
            this.mapBaseName = config.getString("mapName");
        }
        if (config.hasPath("tableSize")) {
            this.tableSize = config.getInt("tableSize");
        }
    }

    public String suggestedTestTag() {
        return "MMTB-" +
                this.numberOfMaps + "w" + //writes
                this.readersPerMap + "c" + //clients
                this.writeRate + "ups" + //updates per second
                this.tableSize + "ts" + //table size
                this.benchmarkWrites + "x"; //times
    }

    public String testInformation() {
        StringBuilder output = new StringBuilder();

        output.append("MergedMapTestBench\n");
        output.append("NumberOfMaps: " + this.numberOfMaps + "\n");
        output.append("ReadersPerMap: " + this.readersPerMap + "\n");
        output.append("WriteRate: " + this.writeRate + " entries/second\n");
        output.append("tableSize: " + this.tableSize + "\n");
        output.append("benchmarkWrites: " + this.benchmarkWrites + "\n");
        output.append("WarmupWrites: " + this.warmupWrites + "\n");

        return output.toString();
    }

    public void run() throws NotEnoughNodesAvailableException {


        if (mapBaseName.equals("")) {
            mapBaseName = this.generateAndDistributeMapBaseName();
        }

        bookkeeper.setHostname(mpiHostName + '-' + mpiRank);
        int writersPerMap = 1;  //Assume 1 writer per map
        int numberOfReaderNodesNeeded = numberOfMaps * readersPerMap;
        int numberOfWriterNodesNeeded = numberOfMaps * writersPerMap;

        if (numberOfReaderNodesNeeded + numberOfWriterNodesNeeded > worldSize) {
            throw new NotEnoughNodesAvailableException();
        }

        IPv4Addr[] ipAddresses = ArpMergedMapTest.distributeRandomIPSet(this.tableSize, worldRank);

        TestNode node;
        if (worldRank < numberOfReaderNodesNeeded + numberOfWriterNodesNeeded) {


            //Nodes are first divided into which map they correspond to and
            //then subdivided in readers/writers
            int myMapId = worldRank / (writersPerMap + readersPerMap);
            String myMapName = mapBaseName + "_" + myMapId;
            int mapRank = worldRank % (writersPerMap + readersPerMap);
            boolean imTheWriter = mapRank == 0;

            /*
             * Let all the writers setup the map first so that they can create it, and
             * after that is done the readers can open up a connection to it
             * (The kafka topic needs to be created by only one node)
             */
            MergedMap<IPv4Addr, ArpCacheEntry> theMap = null;
            Tuple2<MergedMap<IPv4Addr, ArpCacheEntry>, MergedMapBus<IPv4Addr, ArpCacheEntry>> mapAndBus = null;
            if (imTheWriter) {
                mapAndBus = ArpMergedMap.newArpMapAndBus(myMapName, "node" + worldRank, worldRank % 3);
            }
            try { this.barrier(); } catch (MPIException e) {
                log.error("Error during waiting on barrier after node init", e);
            }
            if (!imTheWriter) {
                mapAndBus = ArpMergedMap.newArpMapAndBus(myMapName, "node" + worldRank, worldRank % 3);
            }
            theMap = mapAndBus._1();
            ArpMergedMapTest testReaderWriter = new ArpMergedMapTest(theMap, mapAndBus._2() , ipAddresses, random);

            if (imTheWriter) {
                //You my friend are a writer
                log.info("Starting writer on " + this.mpiHostName + " to map " + myMapName + " (" + worldRank + "," + worldSize + ")");
                node = new WriterNode(testReaderWriter, writeRate, writeInterval, benchmarkWrites, warmupWrites);
            } else {
                //You my friend will have to be a reader
                log.info("Starting reader on " + this.mpiHostName + " to map " + myMapName + " (" + worldRank + "," + worldSize + ")");
                node = new ReaderNode(testReaderWriter, benchmarkWrites, warmupWrites);

                //FIX: Wait for connection
                try {
                    Thread.sleep(10000l);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

        } else {
            log.info("Node on " + this.mpiHostName + " will not participate in this benchark (" + worldRank + "," + worldSize + ")");
            node = new DummyNode();

            /**
             * TODO: Put the dummy nodes in another MPI group, so they dont have to do all the barriers
             */
            log.debug("Awaiting init of other nodes");
            try { this.barrier(); } catch (MPIException e) {
                log.error("Error during waiting on barrier after node init", e);
            }
        }

        this.nodeTestCycle(node);
    }

    /**
     * Code to start the test, this is the main entry point on every node.
     */

    private static Random random = new Random();

    private static final Logger log =
        LoggerFactory.getLogger(MergedMapTestBench.class);

    public static void main(String[] args) {
        int worldSize = -1;
        int worldRank = -1;

        try {
            MPI.Init(args);
            worldSize = MPI.COMM_WORLD.getSize();
            worldRank = MPI.COMM_WORLD.getRank();
        } catch (Exception e) {
            log.error("Impossible to initialize MPI", e);
            return;
        }

        //

        log.info("Starting size: " + worldSize + ", rank: " + worldRank);

        /**
         * Read configuration file!
         */
        Config configuration = ConfigFactory.load();

        Bookkeeper bookkeeper = new Bookkeeper(
                configuration.getString("MergedMapTestBench.Bookkeeper.basePath"),
                "unknownhost",
                "exp"
        );

        try {
            MergedMapTestBench bench = new MergedMapTestBench(
                    worldSize,
                    worldRank

            );
            bench.setBookkeeper(bookkeeper);
            bench.configureWithConfig(configuration.getConfig("MergedMapTestBench"));

            bench.run();


            if (worldRank == 0) {
                List<String> files = bookkeeper.getPathsToAllLogs("summary");
                for (String file : files) {
                    try {
                        String content = new Scanner(new File(file)).useDelimiter("\\Z").next();
                        System.out.println(content);
                    } catch (Exception e) {

                    }
                }
            }


        } catch(NotEnoughNodesAvailableException e) {
            if (worldRank == 0) {
                System.out.println("NOT ENOUGH NODES AVAILABLE FOR REQUESTED TEST CONFIGURATION");
            }
            log.error("Not enough nodes available");
        }


        try {
            MPI.Finalize();
        } catch (Exception e) {
            log.error("Impossible to finalize MPI", e);
        }

        log.info("Done");

        //Exit code is needed to terminate the mpirun command
        System.exit(0);
    }
}
