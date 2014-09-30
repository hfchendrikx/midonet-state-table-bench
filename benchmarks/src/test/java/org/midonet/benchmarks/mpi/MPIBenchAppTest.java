package org.midonet.benchmarks.mpi;

import java.util.UUID;

import mpi.MPI;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import org.midonet.benchmarks.configuration.MpiConfig;

import static java.net.Inet4Address.getLocalHost;
import static org.junit.Assert.*;
import static org.midonet.benchmarks.mpi.MPIBenchApp.MPITimer;

public class MPIBenchAppTest {
    private static final String[] MPIARGS = new String[0];
    private static final String TEST_NODE = "127.0.0.1";

    // Single node config
    private static final MpiConfig config = new MpiConfig() {
        @Override
        public String getMpiHosts() {
            return TEST_NODE;
        }
    };

    @BeforeClass
    public static void beforeClass() throws Exception {
        MPI.Init(MPIARGS);
    }

    @AfterClass
    public static void afterClass() throws Exception {
        MPI.Finalize();
    }

    private MPIBenchApp app;

    @Before
    public void before() throws Exception {
        app = new MPIBenchApp(MPI.COMM_WORLD.getSize(),
                              MPI.COMM_WORLD.getRank(),
                              config);
    }

    @Test
    public void testBasicSettings() throws Exception {
        // Ranks
        assertEquals(1, app.mpiSize);
        assertEquals(0, app.mpiRank);
        assertEquals(0, app.mpiRoot);
        assertTrue(app.isMpiRoot());

        // Environment
        assertEquals(getLocalHost().getHostAddress(), app.mpiHostIp);

        // Protobuf information
        assertEquals(app.mpiHostIp, app.mpiHostProto.getHostIp());
        assertEquals(app.mpiHostName, app.mpiHostProto.getHostName());
        assertEquals(app.mpiSize, app.mpiHostProto.getSize());
        assertEquals(app.mpiRank, app.mpiHostProto.getRank());

        assertEquals(1, app.mpiProto.getNodeCount());
        assertEquals(1, app.mpiProto.getSize());
    }

    @Test
    public void testTimer() throws Exception {
        MPITimer timer = new MPITimer();
        Thread.sleep(1000);
        timer.freeze();
        assertTrue(timer.usecs() >= 1000000.0);
        assertTrue(timer.msecs() >= 1000.0);
        assertTrue(timer.secs() >= 1.0);
    }

    @Test
    public void testSingleNodeBroadcastUUID() throws Exception {
        UUID nullId = null;
        assertNull(app.broadcastUUID(nullId, app.mpiRoot));

        UUID nonNull = UUID.randomUUID();
        UUID received = app.broadcastUUID(nonNull, app.mpiRoot);
        assertNotNull(received);
        assertEquals(nonNull, received);
    }

    @Test
    public void testSingleNodeAllGatherUUID() throws Exception {
        UUID[] idArray = new UUID[4];
        idArray[0] = UUID.randomUUID();
        idArray[1] = UUID.randomUUID();
        idArray[2] = UUID.randomUUID();
        idArray[3] = UUID.randomUUID();

        UUID[] received = app.allgatherUUID(idArray);
        assertArrayEquals(idArray, received);
    }

    @Test
    public void testSingleNodeGatherDouble() throws Exception {
        double[] array = new double[4];
        array[0] = 0.1;
        array[1] = 0.2;
        array[2] = 0.3;
        array[3] = 0.4;

        double[] received = app.gather(array, app.mpiRoot);
        assertArrayEquals(array, received, 0.01);
    }

    @Test
    public void testSingleNodeGatherLong() throws Exception {
        long[] array = new long[4];
        array[0] = 1;
        array[1] = 2;
        array[2] = 3;
        array[3] = 4;

        long[] received = app.gather(array, app.mpiRoot);
        assertArrayEquals(array, received);
    }

    @Test
    public void testSingleNodeBroadcastLong() throws Exception {
        long[] array = new long[4];
        array[0] = 1;
        array[1] = 2;
        array[2] = 3;
        array[3] = 4;

        long[] received = app.broadcast(array, array.length, app.mpiRoot);
        assertArrayEquals(array, received);
    }

}
