package org.midonet.benchmarks;

import com.typesafe.config.Config;
import mpi.MPI;
import mpi.MPIException;
import org.midonet.benchmarks.latencyNodes.*;
import org.midonet.benchmarks.mpi.MPIBenchApp;
import org.midonet.cluster.data.storage.ArpMergedMap;
import org.midonet.cluster.data.storage.KafkaBus;
import org.midonet.cluster.data.storage.MergedMap;
import org.midonet.conf.MidoNodeConfigurator;
import org.midonet.midolman.state.ArpCacheEntry;
import org.midonet.packets.IPv4Addr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.I0Itec.zkclient.ZkClient;

import java.util.Random;

public class NewLatencyBench extends MPIBenchApp {

    /**
     *
     * @param worldSize Information given by MPI
     * @param worldRank Information given by MPI
     * @param zookeeperClient
     * @param numberOfMaps
     * @param writeRate Number of map entries per second
     * @param writeInterval Interval in which to perform the writes in milliseconds
     * @param readersPerMap
     * @throws NotEnoughNodesAvailableException
     */
    public NewLatencyBench (
            int worldSize,
            int worldRank,
            ZkClient zookeeperClient,
            int numberOfMaps,
            int writeRate,
            int writeInterval,
            int readersPerMap,
            String mapBaseName
    ) throws NotEnoughNodesAvailableException {
        super(worldSize, worldRank, "");


        if (mapBaseName.equals("")) {
            if (this.isMpiRoot()) {
                long changingValue = System.currentTimeMillis();
                try {
                    this.broadcast(new long[]{changingValue}, 1, 0);
                } catch (MPIException e) {
                    log.error("Error during broadcast of mapBaseName", e);
                }

                mapBaseName = "auto" + changingValue;
            } else {
                long value = 0;

                try {
                    long[] valueArray = this.broadcast(null, 1, 0);
                    value = valueArray[0];
                } catch (MPIException e) {
                    log.error("Error during receive of mapBaseName", e);
                }

                mapBaseName = "auto" + value;
            }
        }

        int writersPerMap = 1;  //Assume 1 writer per map
        int numberOfReaderNodesNeeded = numberOfMaps * readersPerMap;
        int numberOfWriterNodesNeeded = numberOfMaps * writersPerMap;

        if (numberOfReaderNodesNeeded + numberOfWriterNodesNeeded > worldSize) {
            throw new NotEnoughNodesAvailableException();
        }

        IPv4Addr[] ipAddresses = ArpMergedMapTest.distributeRandomIPSet(1000, worldRank);

        TestNode node;
        if (worldRank < numberOfReaderNodesNeeded + numberOfWriterNodesNeeded) {


            //Nodes are first divided into which map they correspond to and
            //then if they subdivided in readers/writers
            int myMapId = worldRank / (writersPerMap + readersPerMap);
            String myMapName = mapBaseName + "_" + myMapId;
            int mapRank = worldRank % (writersPerMap + readersPerMap);
            boolean imTheWriter = mapRank == 0;

            zookeeperClient = KafkaBus.zookeeperClient();


            /*
             * Let all the writers setup the map first so that they can create it, and
             * after that is done the readers can open up a connection to it
             * (The kafka topic needs to be created by only one node)
             */
            MergedMap<IPv4Addr, ArpCacheEntry> theMap = null;
            if (imTheWriter) {
                theMap = ArpMergedMap.newArpMap(myMapName, "node" + worldRank, zookeeperClient);
            }
            try { this.barrier(); } catch (MPIException e) {
                log.error("Error during waiting on barrier after node init", e);
            }
            if (!imTheWriter) {
                theMap = ArpMergedMap.newArpMap(myMapName, "node" + worldRank, zookeeperClient);
            }

            ArpMergedMapTest testReaderWriter = new ArpMergedMapTest(theMap, ipAddresses, random);

            if (imTheWriter) {
                //You my friend are a writer
                System.out.println("Starting writer on " + this.mpiHostName + " to map " + myMapName + " (" + worldRank + "," + worldSize + ")");
                node = new WriterNode(testReaderWriter);
            } else {
                //You my friend will have to be a reader
                System.out.println("Starting reader on " + this.mpiHostName + " to map " + myMapName + " (" + worldRank + "," + worldSize + ")");
                node = new ReaderNode(testReaderWriter);
            }

        } else {
            System.out.println("Node on " + this.mpiHostName + " will not participate in this benchark (" + worldRank + "," + worldSize + ")");
            node = new DummyNode();

            /**
             * TODO: Put the dummy nodes in another MPI group, so they dont have to do all the barriers
             */
            log.info("Awaiting init of other nodes");
            try { this.barrier(); } catch (MPIException e) {
                log.error("Error during waiting on barrier after node init", e);
            }
        }


        /**
         * Start of testing
         */

        log.info("Setting up benchmark");
        node.setup();

        log.info("Awaiting setup of other nodes");
        try {
            this.barrier();
        } catch (MPIException e) {
            log.error("Error during waiting on barrier after node setup", e);
        }

        log.info("Starting main part of benchmark");
        node.run();

        try {
            this.barrier();
        } catch (MPIException e) {
            log.error("Error during waiting on barrier after main part of benchmark", e);
        }

        node.postProcessResults();


        System.out.println("Finished on " + this.mpiHostName + " (" + worldRank + "," + worldSize + ")");
    }

    /**
     * Code to start the test, this is the main entry point on every node.
     */

    private static Random random = new Random();

    private static final Logger log =
        LoggerFactory.getLogger(NewLatencyBench.class);

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

        try {
            NewLatencyBench bench = new NewLatencyBench(
                    worldSize,
                    worldRank,
                    null,
                    1,
                    1000,
                    10,
                    1,
                    ""
            );
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
