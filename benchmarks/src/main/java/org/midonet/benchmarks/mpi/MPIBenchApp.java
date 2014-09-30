/*
 * Copyright 2014 Midokura SARL
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.midonet.benchmarks.mpi;

import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import mpi.MPI;
import mpi.MPIException;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.midonet.benchmarks.MpiModels;
import org.midonet.benchmarks.configuration.MpiConfig;
import org.midonet.benchmarks.configuration.ConfigException;

import static java.net.InetAddress.getLocalHost;

/**
 * This is a common base class for mpi-based org.midonet.benchmarks
 */
public class MPIBenchApp {

    private static final String CFGFILE_OPTION = "config";
    private static final String DEFAULT_HOST_IP = "127.0.0.1";
    private static final String DEFAULT_HOST_NAME = "localhost";

    private static final Logger log =
        LoggerFactory.getLogger(MPIBenchApp.class);

    public static class MPITimer {
        private final double tStart;
        private double tEnd;

        public MPITimer() throws MPIException {
            tStart = MPI.wtime();
            tEnd = 0.0;
        }

        public MPITimer freeze() throws MPIException {
            tEnd = MPI.wtime();
            return this;
        }

        public double secs() throws MPIException {
            return (tEnd > tStart) ? tEnd - tStart : MPI.wtime() - tStart;
        }

        public double msecs() throws MPIException {
            return secs() * 1000.0;
        }

        public double usecs() throws MPIException {
            return secs() * 1000000.0;
        }

    }

    protected class MPIBlockDistribution extends BlockDistribution {
        public MPIBlockDistribution(int globalCount) {
            super(globalCount, mpiSize, mpiRank);
        }
    }

    protected static Path getCfgFilePath(String[] args)
        throws ConfigException {
        Option cfgOpt = OptionBuilder
            .isRequired(true)
            .hasArg(true)
            .withLongOpt(CFGFILE_OPTION)
            .withDescription("configuration file")
            .create();
        Options options = new Options();
        options.addOption(cfgOpt);

        CommandLineParser parser = new PosixParser();
        CommandLine cl;
        try {
            cl = parser.parse(options, args);
        } catch (Exception e) {
            throw new ConfigException(e);
        }
        Path cfgPath = Paths.get(cl.getOptionValue(CFGFILE_OPTION));
        if (!Files.exists(cfgPath)) {
            throw new ConfigException(
                "config file does not exist: " + cfgPath.toString());
        }
        return cfgPath.toAbsolutePath();
    }

    // Number of MPI processes in execution set
    protected final int mpiSize;

    // Rank in MPI execution set
    protected final int mpiRank;

    // Rank of the "root" MPI process
    protected final int mpiRoot;

    // Current host
    protected final String mpiHostName;
    protected final String mpiHostIp;

    protected final MpiModels.Mpi mpiProto;
    protected final MpiModels.MpiHost mpiHostProto;

    private final MpiConfig cfg;

    protected MPIBenchApp(int mpiSize, int mpiRank, MpiConfig cfg)
        throws MPIException, ConfigException {
        this.mpiSize = mpiSize;
        this.mpiRank = mpiRank;
        this.mpiRoot = 0;
        this.cfg = cfg;

        String hostIp = DEFAULT_HOST_IP;
        String hostName = DEFAULT_HOST_NAME;
        try {
            hostIp = getLocalHost().getHostAddress();
            hostName = getLocalHost().getCanonicalHostName();
        } catch (UnknownHostException e) {
            log.warn("Unable to retrieve local host info at p{}", mpiRank, e);
        }
        mpiHostIp = hostIp;
        mpiHostName = hostName;


        Set<String> mpiNodes =
            new HashSet<>(Arrays.asList(cfg.getMpiHosts().split(",")));
        int mpiNodeCount = mpiNodes.size();

        mpiHostProto = MpiModels.MpiHost.newBuilder()
                                        .setRank(mpiRank)
                                        .setSize(mpiSize)
                                        .setHostName(mpiHostName)
                                        .setHostIp(mpiHostIp)
                                        .build();
        mpiProto = MpiModels.Mpi.newBuilder()
                                .setSize(mpiSize)
                                .setNodeCount(mpiNodeCount)
                                .setHostList(cfg.getMpiHosts())
                                .build();
    }

    /**
     * Check if the current process the "root" MPI process
     */
    protected boolean isMpiRoot() {
        return mpiRank == mpiRoot;
    }

    /**
     * Broadcast an UUID value from the 'source' process to the rest.
     * Note: an UUID with all bytes to zero is considered equivalent to null
     * (and therefore, converted to null).
     *
     * @param id     is the UUID to transmit, only needs to be set in the
     *               source process.
     * @param source is the rank of the source process.
     * @return for all processes, the transmitted UUID
     */
    protected UUID broadcastUUID(UUID id, int source) throws MPIException {
        long[] msg = {0, 0};
        if (source == mpiRank) {
            if (id != null) {
                msg[0] = id.getMostSignificantBits();
                msg[1] = id.getLeastSignificantBits();
            }
        }
        MPI.COMM_WORLD.bcast(msg, 2, MPI.LONG, source);
        return (msg[0] == 0 && msg[1] == 0) ? null : new UUID(msg[0], msg[1]);
    }

    /**
     * Share partial arrays of UUID to form a single array with data from all
     * processes. All the partial arrays must be of the same size, and can
     * contain nulls (NOTE: an UUID with all bytes to zero is considered
     * equivalent to null).
     *
     * @param idArray is the partial array of UUIDS
     * @return for all processes, the concatenation of all partial arrays
     */
    protected UUID[] allgatherUUID(UUID[] idArray) throws MPIException {
        int baseArraySize = idArray.length * 2;
        long[] smsg = new long[baseArraySize];
        for (int i = 0; i < idArray.length; i++) {
            if (idArray[i] == null) {
                smsg[2 * i] = 0;
                smsg[2 * i + 1] = 0;
            } else {
                smsg[2 * i] = idArray[i].getMostSignificantBits();
                smsg[2 * i + 1] = idArray[i].getLeastSignificantBits();
            }
        }

        // Note: passing the send buffer size as the second length
        // for the receiving buffer is NOT a bug.
        long[] rmsg = new long[baseArraySize * mpiSize];
        MPI.COMM_WORLD.allGather(smsg, baseArraySize, MPI.LONG,
                                 rmsg, baseArraySize, MPI.LONG);

        UUID[] allIds = new UUID[idArray.length * mpiSize];
        for (int i = 0; i < (idArray.length * mpiSize); i++) {
            if (rmsg[2 * i] == 0 && rmsg[2 * i + 1] == 0) {
                allIds[i] = null;
            } else {
                allIds[i] = new UUID(rmsg[2 * i], rmsg[2 * i + 1]);
            }
        }
        return allIds;
    }

    /**
     * Send the minimum value from all processes to the target process
     *
     * @return the target process gets the minimum value, and the others
     * get their own value
     */
    protected long reduceMin(long value, int target) throws MPIException {
        long[] buff = {value};
        MPI.COMM_WORLD.reduce(buff, 1, MPI.LONG, MPI.MIN, target);
        return buff[0];
    }

    /**
     * Send the maximum value from all processes to the target process
     *
     * @return the target process gets the maximum value, and the others
     * get their own value
     */
    protected long reduceMax(long value, int target) throws MPIException {
        long[] buff = {value};
        MPI.COMM_WORLD.reduce(buff, 1, MPI.LONG, MPI.MAX, target);
        return buff[0];
    }

    /**
     * Send the sum of the value from all processes to the target process
     *
     * @return the target process gets the sum of the values, and the others
     * get their own value
     */
    protected long reduceSum(long value, int target) throws MPIException {
        long[] buff = {value};
        MPI.COMM_WORLD.reduce(buff, 1, MPI.LONG, MPI.SUM, target);
        return buff[0];
    }

    /**
     * Collect partial arrays from all processes. All partial arrays must be
     * of the same size.
     *
     * @param partialData the local partial array
     * @param target      the process that will collect the union
     * @return the target process receives an array with the concatenation of
     * all partial arrays; the other processes get a null value.
     */
    protected double[] gather(double[] partialData, int target)
        throws MPIException {
        if (mpiRank == target) {
            double[] data = new double[partialData.length * mpiSize];
            MPI.COMM_WORLD.gather(partialData, partialData.length, MPI.DOUBLE,
                                  data, partialData.length, MPI.DOUBLE, target);
            return data;
        } else {
            MPI.COMM_WORLD.gather(partialData, partialData.length, MPI.DOUBLE,
                                  null, 0, MPI.DOUBLE, target);
            return null;
        }
    }

    protected long[] gather(long[] partialData, int target)
        throws MPIException {
        if (mpiRank == target) {
            long[] data = new long[partialData.length * mpiSize];
            MPI.COMM_WORLD.gather(partialData, partialData.length, MPI.LONG,
                                  data, partialData.length, MPI.LONG, target);
            return data;
        } else {
            MPI.COMM_WORLD.gather(partialData, partialData.length, MPI.LONG,
                                  null, 0, MPI.LONG, target);
            return null;
        }
    }

    /**
     * Broadcast an array to all processes from the given source
     * @param localData is the data to be broadcasted (can be null on
     *                  non-source processes.
     * @param size is the length of the broadcasted array; must be set
     *             (and equal) on all processes.
     * @param source is the rank of the source process.
     * @return the broadcasted data for the source, and a newly allocated
     * array with the received data for the rest of the processes
     */
    protected long[] broadcast(long[] localData, int size, int source)
        throws MPIException {
        long[] data = (source == mpiRank) ? localData : new long[size];
        MPI.COMM_WORLD.bcast(data, size, MPI.LONG, source);
        return data;
    }

    /**
     * Convenient wrapper around global barrier
     */
    protected static void barrier() throws MPIException {
        MPI.COMM_WORLD.barrier();
    }

}


