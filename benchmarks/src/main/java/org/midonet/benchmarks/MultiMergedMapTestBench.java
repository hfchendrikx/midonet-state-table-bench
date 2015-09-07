package org.midonet.benchmarks;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import mpi.MPI;
import mpi.MPIException;
import org.I0Itec.zkclient.ZkClient;
import org.midonet.benchmarks.latencyNodes.*;
import org.midonet.benchmarks.mpi.MPIBenchApp;
import org.midonet.cluster.data.storage.ArpMergedMap;
import org.midonet.cluster.data.storage.KafkaBus;
import org.midonet.cluster.data.storage.MergedMap;
import org.midonet.midolman.state.ArpCacheEntry;
import org.midonet.packets.IPv4Addr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.Tuple2;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Scanner;

/**
 * The MultiMergedMapTestBench is for testing the latency of the kafka cluster with a large amount of maps.
 *
 * An example of this with 6 maps, 3 maps per writer, and 3 maps per reader
 *
 *.    Writer        Writer
 *.   /  |  \       /  |  \
 *. Map Map Map   Map Map Map
 *.   \  |  /       \  |  /
 *.    Reader        Reader
 */
public class MultiMergedMapTestBench extends TestBench {

    int tableSize = 1000;
    String mapBaseName;

    ZkClient zookeeperClient;

    int numberOfMaps;
    int writeRatePerMap;
    int maxNumberOfMapsPerWriter;
    int maxNumberOfMapsPerReader;
    int benchmarkWrites;
    int warmupWrites;

    /**
     *
     * @param worldSize Information given by MPI
     * @param worldRank Information given by MPI
     */

    public MultiMergedMapTestBench(
            int worldSize,
            int worldRank
    ) {
        super(worldSize, worldRank, "");

        this.worldSize = worldSize;
        this.worldRank = worldRank;
    }

    public void setBookkeeper(Bookkeeper bookkeeper) {
        this.bookkeeper = bookkeeper;
    }

    public void configureWithConfig(Config config) {
        this.numberOfMaps = config.getInt("numberOfMaps");
        this.writeRatePerMap = config.getInt("writeRatePerMap");
        this.maxNumberOfMapsPerReader = config.getInt("maxNumberOfMapsPerReader");
        this.maxNumberOfMapsPerWriter = config.getInt("maxNumberOfMapsPerWriter");
        this.benchmarkWrites = config.getInt("benchMarkWrites");
        this.warmupWrites = config.getInt("warmupWrites");

        this.mapBaseName = config.getString("mapName");
        this.tableSize = config.getInt("tableSize");
    }

    public void updateConfigurationWithConfig(Config config) {
        if (config.hasPath("numberOfMaps")) {
            this.numberOfMaps = config.getInt("numberOfMaps");
        }
        if (config.hasPath("writeRatePerMap")) {
            this.writeRatePerMap = config.getInt("writeRatePerMap");
        }
        if (config.hasPath("maxNumberOfMapsPerReader")) {
            this.maxNumberOfMapsPerReader = config.getInt("maxNumberOfMapsPerReader");
        }
        if (config.hasPath("maxNumberOfMapsPerWriter")) {
            this.maxNumberOfMapsPerWriter = config.getInt("maxNumberOfMapsPerWriter");
        }
        if (config.hasPath("benchMarkWrites")) {
            this.benchmarkWrites = config.getInt("benchMarkWrites");
        }
        if (config.hasPath("warmupWrites")) {
            this.warmupWrites = config.getInt("warmupWrites");
        }
        if (config.hasPath("mapName")) {
            this.mapBaseName = config.getString("mapName");
        }
        if (config.hasPath("tableSize")) {
            this.tableSize = config.getInt("tableSize");
        }
    }

    public String suggestedTestTag() {
        return "MultiMMTB-" +
                this.maxNumberOfMapsPerWriter + "mpw" + //writers
                this.maxNumberOfMapsPerReader + "mpr" + //clients
                this.numberOfMaps + "maps" + //maps
                this.writeRatePerMap + "ups" + //updates per second
                this.tableSize + "ts" + //table size
                this.benchmarkWrites + "x"; //times
    }

    public String testInformation() {
        StringBuilder output = new StringBuilder();

        output.append("MultiMergedMapTestBench\n");
        output.append("NumberOfMaps: " + this.numberOfMaps + "\n");

        output.append("maxNumberOfMapsPerWriter: " + this.maxNumberOfMapsPerWriter + " \n");
        output.append("maxNumberOfMapsPerReader: " + this.maxNumberOfMapsPerReader + " \n");
        output.append("writeRatePerMap: " + this.writeRatePerMap + " entries/second\n");

        output.append("benchmarkWrites: " + this.benchmarkWrites + "\n");
        output.append("WarmupWrites: " + this.warmupWrites + "\n");

        return output.toString();
    }

    private String[] calculateMapNames(int nodeId, int maxNumberOfMaps, int mapsPerNode) {

        int amount = Math.min((nodeId + 1) * mapsPerNode, maxNumberOfMaps) - (nodeId * mapsPerNode);

        String[] mapNames = new String[amount];

        for (int i=0; i<amount; i++) {
            int mapId = nodeId * mapsPerNode + i;
            mapNames[i] = mapBaseName + "_" + mapId;
        }

        return mapNames;
    }

    public void run() throws NotEnoughNodesAvailableException {

        if (mapBaseName.equals("")) {
            mapBaseName = this.generateAndDistributeMapBaseName();
        }
        bookkeeper.setHostname(mpiHostName + '-' + mpiRank);

        int numberOfReaderNodesNeeded = numberOfMaps / maxNumberOfMapsPerReader;
        if (numberOfMaps % maxNumberOfMapsPerReader > 0) numberOfReaderNodesNeeded += 1;

        int numberOfWriterNodesNeeded = numberOfMaps / maxNumberOfMapsPerWriter;
        if (numberOfMaps % maxNumberOfMapsPerWriter > 0) numberOfWriterNodesNeeded += 1;

        if (numberOfReaderNodesNeeded + numberOfWriterNodesNeeded > worldSize) {
            throw new NotEnoughNodesAvailableException();
        }

        IPv4Addr[] ipAddresses = ArpMergedMapTest.distributeRandomIPSet(this.tableSize, worldRank);

        TestNode node;
        if (worldRank < numberOfReaderNodesNeeded + numberOfWriterNodesNeeded) {

            //rank 0 - numberOfWriterNodesNeeded-1 are writers
            //others are readers
            boolean iAmAWriter = worldRank < numberOfWriterNodesNeeded;
            int myId = 0;
            if (iAmAWriter) {
                myId = worldRank;
            } else {
                myId = worldRank - numberOfWriterNodesNeeded;
            }
            String[] myMaps = calculateMapNames(
                    myId,
                    numberOfMaps,
                    iAmAWriter ? maxNumberOfMapsPerWriter : maxNumberOfMapsPerReader
            );
            int numberOfMapsIHave = myMaps.length;


            zookeeperClient = KafkaBus.zookeeperClient();
            MergedMap<IPv4Addr, ArpCacheEntry>[] maps = new MergedMap[numberOfMapsIHave];
            KafkaBus<IPv4Addr, ArpCacheEntry>[] busses = new KafkaBus[numberOfMapsIHave];

            /*
             * Let all the writers setup the map first so that they can create it, and
             * after that is done the readers can open up a connection to it
             * (The kafka topic needs to be created by only one node)
             */
            Tuple2<MergedMap<IPv4Addr, ArpCacheEntry>, KafkaBus<IPv4Addr, ArpCacheEntry>> mapAndBus = null;
            String mapNamesString = "";
            for (String mapName : myMaps) {
                mapNamesString += mapName + " , ";
            }

            if (iAmAWriter) {

                for (int i=0; i<numberOfMapsIHave; i++) {
                    mapAndBus = ArpMergedMap.newArpMapAndReturnKafkaBus(myMaps[i], "node" + worldRank, zookeeperClient);
                    maps[i] = mapAndBus._1();
                    busses[i] = mapAndBus._2();
                }

                try { this.barrier(); } catch (MPIException e) {
                    log.error("Error during waiting on barrier after node init (writer)", e);
                }

                log.info("Starting writer on " + this.mpiHostName + " to maps " + mapNamesString + " (" + worldRank + "," + worldSize + ")");

                node = new MultiMapWriterNode(maps, busses, benchmarkWrites, warmupWrites, writeRatePerMap, ipAddresses, random);

            } else {
                try { this.barrier(); } catch (MPIException e) {
                    log.error("Error during waiting on barrier after node init (reader)", e);
                }

                for (int i=0; i<numberOfMapsIHave; i++) {
                    mapAndBus = ArpMergedMap.newArpMapAndReturnKafkaBus(myMaps[i], "node" + worldRank, zookeeperClient);
                    maps[i] = mapAndBus._1();
                    busses[i] = mapAndBus._2();
                }

                log.info("Starting reader on " + this.mpiHostName + " to maps " + mapNamesString + " (" + worldRank + "," + worldSize + ")");

                node = new MultiMapReaderNode(maps, busses, benchmarkWrites, warmupWrites, tableSize);
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
        LoggerFactory.getLogger(MultiMergedMapTestBench.class);

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
                configuration.getString("MultiMergedMapTestBench.Bookkeeper.basePath"),
                "unknownhost",
                "exp"
        );

        try {
            MultiMergedMapTestBench bench = new MultiMergedMapTestBench(
                    worldSize,
                    worldRank

            );
            bench.setBookkeeper(bookkeeper);
            bench.configureWithConfig(configuration.getConfig("MultiMergedMapTestBench"));
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
