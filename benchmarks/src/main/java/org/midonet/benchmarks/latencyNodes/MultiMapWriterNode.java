package org.midonet.benchmarks.latencyNodes;

import mpi.MPI;
import org.midonet.benchmarks.MultiMergedMapTestBench;
import org.midonet.benchmarks.mpi.MPIBenchApp;
import org.midonet.cluster.data.storage.KafkaBus;
import org.midonet.cluster.data.storage.MergedMap;
import org.midonet.midolman.state.ArpCacheEntry;
import org.midonet.packets.IPv4Addr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintStream;
import java.util.Observable;
import java.util.Random;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.LockSupport;

/**
 * Created by huub on 7-9-15.
 */
public class MultiMapWriterNode extends TimestampedNode {


    private static final Logger log =
            LoggerFactory.getLogger(MultiMapWriterNode.class);

    MergedMap<IPv4Addr, ArpCacheEntry>[] maps;
    KafkaBus<IPv4Addr, ArpCacheEntry>[] busses;
    int benchmarkWrites;
    int warmupWrites;
    int writeRate;
    IPv4Addr[] ipSet;
    Random random;
    int noSleepCounter = 0;

    public MultiMapWriterNode(
            MergedMap<IPv4Addr, ArpCacheEntry>[] maps,
            KafkaBus<IPv4Addr, ArpCacheEntry>[] busses,
            int benchmarkWrites,
            int warmupWrites,
            int writeRate,
            IPv4Addr[] ipSet,
            Random theOracle
    ) {
        this.maps = maps;
        this.busses = busses;
        this.benchmarkWrites = benchmarkWrites;
        this.warmupWrites = warmupWrites;
        this.writeRate = writeRate;
        this.ipSet = ipSet;
        this.random = theOracle;
    }



    @Override
    public void timestampedSetup() {
        for(int i = 0;i<maps.length;i++) {
            MergedMap currentMap = maps[i];

            //Load full map
            for (int j=0;j<ipSet.length;j++) {
                currentMap.putOpinion(
                        ipSet[j],
                        ArpMergedMapTest.createEntry(MPIBenchApp.getTime())
                );
            }

            //Do random warmup writes
            for (int j=0;j<warmupWrites;j++) {
                currentMap.putOpinion(
                        ipSet[random.nextInt(ipSet.length)],
                        ArpMergedMapTest.createEntry(MPIBenchApp.getTime())
                );
            }
        }
    }

    @Override
    public void timestampedRun() {
        try {
            //Delay every writernode with rank * 100us
            LockSupport.parkNanos(MPI.COMM_WORLD.getRank() * 100000);
        } catch (Exception e) {
            log.error("Exception during delay sleep in writer node", e);
        }

        int written = 0;
        int i = 0;
        boolean noSleep = false;
        long intervalDurationNanos = (long)(1000000000 / writeRate);
        long wokeUpAt = System.nanoTime();

        while (written < benchmarkWrites) {

            for (i=0; i<maps.length; i++) {
                maps[i].putOpinion(
                        ipSet[random.nextInt(ipSet.length)],
                        ArpMergedMapTest.createEntry(MPIBenchApp.getTime())
                );
            }

            /*
            try {
                Thread.sleep(1000 / writeRate);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            */

            noSleep = true;
            while ((System.nanoTime() - wokeUpAt) < (intervalDurationNanos)) {
                LockSupport.parkNanos(intervalDurationNanos - (System.nanoTime() - wokeUpAt));
                noSleep = false;
            }

            if (noSleep) {
                wokeUpAt = System.nanoTime();
                noSleepCounter++;
            } else {
                wokeUpAt += intervalDurationNanos;
            }


            written++;
        }
    }

    @Override
    public void shutdown() {

        try {
            //There is one node that can have a shorter maps.length than the rest of the nodes
            //for now this is ok
            long usPerNode = MultiMergedMapTestBench.MAP_SHUTDOWN_THROTTLE_NS_PER_MAP * maps.length;
            LockSupport.parkNanos(MPI.COMM_WORLD.getRank() * usPerNode);
        } catch (Exception e) {
            log.error("Exception during delay sleep in shutdown", e);
        }

        for (KafkaBus bus : busses) {
            bus.shutdown();
        }
    }

    @Override
    public String postProcessResults(Bookkeeper bookkeeper) {
        super.postProcessResults(bookkeeper);

        if (noSleepCounter > 0) {
            log.info("Skipped sleep " + noSleepCounter + " times");
        }

        double benchmarkDurationSeconds = (endBenchmark - startBenchmark) / 1000.0;

        PrintStream logFile = bookkeeper.getFileWriter("write-summary");
        logFile.println("skippedsleep=" + this.noSleepCounter);
        logFile.println("avgwriterate=" + (benchmarkWrites / benchmarkDurationSeconds));

        return "";
    }
}
