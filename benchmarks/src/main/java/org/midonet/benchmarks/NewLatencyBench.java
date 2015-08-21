package org.midonet.benchmarks;

import mpi.MPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NewLatencyBench {

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

        System.out.println("Testin...");
        System.out.println("WorldSize: " + worldSize);
        System.out.println("WorldRank: " + worldRank);

        try {
            MPI.Finalize();
        } catch (Exception e) {
            log.error("Impossible to finalize MPI", e);
        }
    }
}
