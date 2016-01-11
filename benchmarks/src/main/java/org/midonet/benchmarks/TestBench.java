package org.midonet.benchmarks;

import com.typesafe.config.Config;
import mpi.MPI;
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
    private static final Logger status_log =
            LoggerFactory.getLogger("status");

    public void setBookkeeper(Bookkeeper bookkeeper) {
        this.bookkeeper = bookkeeper;
    }

    abstract public String testInformation();
    abstract public String suggestedTestTag();
    abstract public void configureWithConfig(Config config);
    abstract public void updateConfigurationWithConfig(Config config);

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

    protected void logStatusMessage(String message) {
        log.debug(message);

        if (this.isMpiRoot()) {
            status_log.info(message);
        }
    }

    protected void throttledSetup(TestNode node) {
        int nodeThatCanStartWarmup = 0;

        while (nodeThatCanStartWarmup < mpiSize) {
            if (nodeThatCanStartWarmup == mpiRank) {
                status_log.info("Node " + mpiRank + " setup begin");
                node.setup();
                status_log.info("Node " + mpiRank + " setup end");
            }

            try {
                MPI.COMM_WORLD.barrier();

                if (isMpiRoot()) {
                    nodeThatCanStartWarmup++;
                    this.broadcast(0, nodeThatCanStartWarmup);
                } else {
                    nodeThatCanStartWarmup = this.broadcast(0, 0);
                }
            } catch (MPIException e) {
                log.error("MPIException during throttled setup broadcast/barrier (rank=" + mpiRank + ")", e);
                throw new RuntimeException("Unable to continue due to MPIException at setup");
            }

        }

    }

    protected void nodeTestCycle(TestNode node) {

        log.debug("Gathering all nodes for start of test cycle");
        try { this.barrier(); } catch (MPIException e) {
            log.error("Error during waiting on barrier before nodeTestCycle", e);
        }

        /**
         * Start of testing
         * All exceptions need to be caught, because if one node misses a
         * barrier the whole test gets stuck.
         */

        logStatusMessage("Setting up benchmark");
        try {
            this.throttledSetup(node);
        } catch (Exception e) {
            log.error("Exception during node.setup()", e);
        }

        logStatusMessage("Awaiting setup of other nodes");
        try {
            this.barrier();
        } catch (MPIException e) {
            log.error("Error during waiting on barrier after node setup", e);
        }

        logStatusMessage("Starting main part of benchmark");
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

        logStatusMessage("Shutting down");
        try {
            node.shutdown();
            status_log.info("Node " + mpiRank + " shutdown successful");
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

        logStatusMessage("Finished on " + this.mpiHostName + " (" + worldRank + "," + worldSize + ")");
    }

    public abstract void run() throws NotEnoughNodesAvailableException;

    public static void startupHelper() {
        //TODO: Add main() function common code here
    }

}
