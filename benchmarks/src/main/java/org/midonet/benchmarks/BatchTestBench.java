package org.midonet.benchmarks;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigList;
import mpi.MPI;
import mpi.MPIException;
import org.midonet.benchmarks.latencyNodes.Bookkeeper;
import org.midonet.benchmarks.mpi.MPIBenchApp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.login.Configuration;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import java.util.Scanner;

/**
 * Created by huub on 27-8-15.
 */
public class BatchTestBench extends MPIBenchApp {

    private static final Logger log =
            LoggerFactory.getLogger(BatchTestBench.class);

    int batchRunNumber = -1;
    Config currentRunConfiguration;
    Config batchConfiguration;
    boolean currentRunExists = false;

    public BatchTestBench(
            int batchRunNumber,
            int worldRank,
            int worldSize,
            Config config
    ) {
        super(worldSize, worldRank, "");
        this.batchRunNumber = batchRunNumber;
        this.broadcastBatchRunNumber();
        batchConfiguration = config;
        List<? extends Config> runs = config.getConfigList("runs");

        if (runs.size() >= batchRunNumber) {
            currentRunConfiguration = runs.get(batchRunNumber-1);
            currentRunExists = true;
        }
    }

    private void broadcastBatchRunNumber() {

        if (mpiRank == 0) {
            try {
                broadcast(new int[]{batchRunNumber}, 1, 0);
            } catch (MPIException e) {
                log.error("Broadcasting of batch run number failed at root");
            }
        } else {
            try {
                int[] number = broadcast(new int[]{}, 1, 0);
                this.batchRunNumber = number[0];
            } catch (MPIException e) {
                log.error("Broadcasting of batch run number failed at non-root");
            }
        }
    }

    public boolean isBatchRunFinished() {
        return !currentRunExists;
    }

    public void run() {
        String benchmarkType = currentRunConfiguration.getString("bench");
        switch (benchmarkType) {
            case "MergedMapTestBench":
                this.runMergedMapTestBench();
                break;
            default:
                log.warn("Unknown benchmark type '{}' found in run configuration",benchmarkType);
                break;
        }
    }

    private void runMergedMapTestBench() {
        MergedMapTestBench testBench = new MergedMapTestBench(mpiSize, mpiRank);
        testBench.updateConfigurationWithConfig(batchConfiguration.getConfig("defaultConfig.MergedMapTestBench"));
        testBench.updateConfigurationWithConfig(currentRunConfiguration.getConfig("MergedMapTestBench"));

        Bookkeeper bookkeeper = new Bookkeeper(
                batchConfiguration.getString("Bookkeeper.basePath"),
                mpiHostName + "-" + mpiRank,
                testBench.suggestedTestTag());

        testBench.setBookkeeper(bookkeeper);

        if (mpiRank == 0) {
            System.out.println(testBench.testInformation());
        }

        try {
            testBench.run();

            if (mpiRank == 0) {
                PrintStream logFile = bookkeeper.getFileWriter("configuration");
                logFile.println(testBench.testInformation());
                logFile.println("\n\nConfigfile:\n");
                logFile.println(batchConfiguration.toString());
                logFile.close();

            }
        } catch (NotEnoughNodesAvailableException e) {
            if (mpiRank == 0) {
                PrintStream logFile = bookkeeper.getFileWriter("NOT_ENOUGH_NODES");
                logFile.println("Not enough nodes were available during execution");
                logFile.close();
                log.error("NOT ENOUGH NODES AVAILABLE FOR TEST", e);
            }
        }
    }

    public static void main(String[] args) {
        int worldSize = -1;
        int worldRank = -1;
        int timesStarted = Integer.parseInt(System.getProperty("bench.restarts"));
        String mainDirectory = System.getProperty("bench.mainDirectory");
        pathToRestartFile = mainDirectory + "/restart_jvm";

        try {
            MPI.Init(args);
            worldSize = MPI.COMM_WORLD.getSize();
            worldRank = MPI.COMM_WORLD.getRank();
        } catch (Exception e) {
            log.error("Impossible to initialize MPI", e);
            return;
        }

        log.info("Starting size: {} rank: {} run-number: {}", worldSize, worldRank, timesStarted);

        if (worldRank == 0) {
            ownsRestartFile = true;
        }

        /**
         * Read configuration file!
         */
        Config configuration = ConfigFactory.load();
        BatchTestBench bench = new BatchTestBench(timesStarted, worldRank, worldSize, configuration.getConfig("BatchTestBench"));

        if (bench.isBatchRunFinished()) {
            if (worldRank == 0) {
                setRestartJvm(false);
            }
        } else {

            try {
                bench.run();
            } catch (Exception e) {
                log.error("Exception during bench.run()", e);
            }

            if (worldRank == 0) {
                setRestartJvm(true);
            }
        }

        //This barrier is necessary just before exit to make sure that processes dont
        //exit before the restart_jvm file is modified
        try {
            bench.barrier();
        } catch (MPIException e) {
            log.error("Error during waiting on barrier at exit of jvm", e);
        }

        try {
            MPI.Finalize();
        } catch (Exception e) {
            log.error("Impossible to finalize MPI", e);
        }

        //Exit code is needed to terminate the mpirun command
        System.exit(0);
    }

    private static String pathToRestartFile;
    private static boolean ownsRestartFile = false;
    public static void setRestartJvm(boolean restart) {

        if (!ownsRestartFile) {
            log.warn("Wrong process is changing the jvm restart parameter");
            return;
        }

        File restartFile = new File(pathToRestartFile);
        if (restart && !restartFile.exists()) {
            try {
                restartFile.createNewFile();
            } catch (IOException e) {
                log.error("Creation of the restart_jvm file failed");
            }
        } else if (!restart && restartFile.exists()) {
            restartFile.delete();
        }
    }

}
