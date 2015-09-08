package org.midonet.benchmarks.latencyNodes;

import mpi.MPI;
import org.midonet.benchmarks.mpi.MPIBenchApp;
import org.midonet.cluster.data.storage.KafkaBus;
import org.midonet.cluster.data.storage.MergedMap;
import org.midonet.midolman.state.ArpCacheEntry;
import org.midonet.packets.IPv4Addr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Observable;
import java.util.Random;

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

        int written = 0;
        int i = 0;

        while (written < benchmarkWrites) {

            for (i=0; i<maps.length; i++) {
                maps[i].putOpinion(
                        ipSet[random.nextInt(ipSet.length)],
                        ArpMergedMapTest.createEntry(MPIBenchApp.getTime())
                );
            }

            try {
                //Not a very precise throttling like this
                Thread.sleep((long)(1000 / writeRate));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            written++;
        }

    }

    @Override
    public void shutdown() {
        for (KafkaBus bus : busses) {
            bus.shutdown();
        }
    }

    @Override
    public String postProcessResults(Bookkeeper bookkeeper) {
        super.postProcessResults(bookkeeper);
        return "";
    }
}
