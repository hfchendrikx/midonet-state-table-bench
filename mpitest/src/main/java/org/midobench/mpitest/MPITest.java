/*                                                                              
 * Adapted from usual mpi test programs.
 */

package org.midobench.mpitest;

import mpi.MPI;
import mpi.MPIException;

/**
 * Main application for the MPI test.
 */
public class MPITest {

    // Data size in bytes
    final static int DATASIZE = 256;
    final static int REPETITIONS = 1000;

    public static void main(String[] args) throws MPIException {
        MPI.Init(args);

        int worldRank = MPI.COMM_WORLD.getRank();
        int worldSize = MPI.COMM_WORLD.getSize();
        String host = MPI.getProcessorName();

        System.out.print(String.format("Running instance %d/%d on %s\n",
                                       worldRank, worldSize, host));
        System.out.flush();

        MPI.COMM_WORLD.barrier();

        // Determine ping starters for ping pong test
        int peer = worldRank ^ 1;
        if ((peer < worldSize) && ((peer & 1) != 0)) {
            System.out.print(String.format("%d pings %d\n", worldRank, peer));
            System.out.flush();
        }

        byte[] tBuf = new byte[DATASIZE];
        byte[] rBuf = new byte[DATASIZE];

        for (int i = 0; i < DATASIZE; i++) {
            tBuf[i] = (byte)(i & 255);
            rBuf[i] = 0;
        }

        int reps = REPETITIONS;

        MPI.COMM_WORLD.barrier();

        double tStart = MPI.wtime();
        if (peer < worldSize) {
            if ((worldRank & 1 ) != 0) {
                reps --;
                MPI.COMM_WORLD.recv(rBuf, DATASIZE, MPI.BYTE, peer, 0);
                // Check received data
                for (int i = 0; i < DATASIZE; i++) {
                    if (rBuf[i] != (byte)(i & 255)) {
                        System.err.print(String.format(
                            "Error: proc %d: got %d instead of %d at pos %d\n",
                            worldRank, rBuf[i], (i & 255), i));
                        System.err.flush();
                    }
                    rBuf[i] = 0;
                }
            }

            while (reps-- > 0) {
                MPI.COMM_WORLD.send(tBuf, DATASIZE, MPI.BYTE, peer, 0);
                MPI.COMM_WORLD.recv(rBuf, DATASIZE, MPI.BYTE, peer, 0);
                // Check received data
                for (int i = 0; i < DATASIZE; i++) {
                    if (rBuf[i] != (byte)(i & 255)) {
                        System.err.print(String.format(
                            "Error: proc %d: got %d instead of %d at pos %d\n",
                            worldRank, rBuf[i], (i & 255), i));
                        System.err.flush();
                    }
                    rBuf[i] = 0;
                }
            }

            if ((worldRank & 1) != 0) {
                MPI.COMM_WORLD.send(tBuf, DATASIZE, MPI.BYTE, peer, 0);
            }
        }
        double tEnd = MPI.wtime();

        MPI.COMM_WORLD.barrier();

        // Elapsed time in useconds (round trip)
        // Only consider senders time
        double t = (tEnd - tStart) * 1000000.0 / REPETITIONS;

        if ((peer < worldSize) && ((peer & 1) != 0)) {
            System.out.print(String.format(
                "roundtrip %d <-> %d: %8d bytes %9.2f uSec\n",
                worldRank, peer, DATASIZE, t));
            System.out.flush();
            // Pass elapsed time to receivers
            double[] timebuf = {t};
            MPI.COMM_WORLD.send(timebuf, 1, MPI.DOUBLE, peer, 0);
        }
        if ((peer < worldSize) && ((peer & 1) == 0)) {
            // Recive elapsed time from peer
            double[] timebuf = new double[1];
            MPI.COMM_WORLD.recv(timebuf, 1, MPI.DOUBLE, peer, 0);
            t = timebuf[0];
        }

        MPI.COMM_WORLD.barrier();

        // Get max/min elapsed times
        double[] elapsed = {t};
        double[] maxTime = new double[1];
        MPI.COMM_WORLD.reduce(elapsed, maxTime, 1, MPI.DOUBLE, MPI.MAX, 0);
        if (worldRank == 0) {
            System.out.print(String.format("max time: %9.2f uSec\n",
                                           maxTime[0]));
        }

        MPI.COMM_WORLD.barrier();

        MPI.Finalize();
    }
}

