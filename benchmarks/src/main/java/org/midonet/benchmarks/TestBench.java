package org.midonet.benchmarks;

import mpi.MPIException;
import org.midonet.benchmarks.latencyNodes.Bookkeeper;
import org.midonet.benchmarks.latencyNodes.TestNode;
import org.midonet.benchmarks.mpi.MPIBenchApp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by huub on 7-9-15.
 */
public abstract class TestBench extends MPIBenchApp {


    protected int worldSize;
    protected int worldRank;

    protected Bookkeeper bookkeeper;

    protected TestBench(int mpiSize, int mpiRank, String mpiHosts) {
        super(mpiSize, mpiRank, mpiHosts);
    }

    private static final Logger log =
            LoggerFactory.getLogger(TestBench.class);

    public void setBookkeeper(Bookkeeper bookkeeper) {
        this.bookkeeper = bookkeeper;
    }
    abstract public String testInformation();
    abstract public String suggestedTestTag();

    protected String generateAndDistributeMapBaseName() {
        String mapBaseName = "";

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
                long[] valueArray = this.broadcast(new long[]{}, 1, 0);
                value = valueArray[0];
            } catch (MPIException e) {
                log.error("Error during receive of mapBaseName", e);
            }

            mapBaseName = "auto" + value;
        }

        return mapBaseName;
    }

    protected void nodeTestCycle(TestNode node) {
        /**
         * Start of testing
         * All exceptions need to be caught, because if one node misses a
         * barrier the whole test gets stuck.
         */

        log.debug("Setting up benchmark");
        try {
            node.setup();
        } catch (Exception e) {
            log.error("Exception during node.setup()", e);
        }

        log.debug("Awaiting setup of other nodes");
        try {
            this.barrier();
        } catch (MPIException e) {
            log.error("Error during waiting on barrier after node setup", e);
        }

        log.debug("Starting main part of benchmark");
        try {
            node.run();
        } catch (Exception e) {
            log.error("Exception during node.run()", e);
        }

        try {
            this.barrier();
        } catch (MPIException e) {
            log.error("Error during waiting on barrier after main part of benchmark", e);
        }

        try {
            node.shutdown();
        } catch (Exception e) {
            log.error("Exception during node.shutdown()", e);
        }

        try {
            node.postProcessResults(bookkeeper);
        } catch (Exception e) {
            log.error("Exception during node.postProcessResults()", e);
        }

        try {
            this.barrier();
        } catch (MPIException e) {
            log.error("Error during waiting on barrier after postproccessing of benchmark", e);
        }


        log.info("Finished on " + this.mpiHostName + " (" + worldRank + "," + worldSize + ")");
    }

    public abstract void run() throws NotEnoughNodesAvailableException;

    public static void startupHelper() {
        //TODO: Add main() function common code here
    }

}
