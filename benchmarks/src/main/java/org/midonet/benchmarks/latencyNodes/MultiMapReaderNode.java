package org.midonet.benchmarks.latencyNodes;

import org.midonet.benchmarks.StatUtils;
import org.midonet.benchmarks.mpi.MPIBenchApp;
import org.midonet.cluster.data.storage.ArpMergedMap;
import org.midonet.cluster.data.storage.KafkaBus;
import org.midonet.cluster.data.storage.MergedMap;
import org.midonet.midolman.state.ArpCacheEntry;
import org.midonet.packets.IPv4Addr;
import org.midonet.util.reactivex.AwaitableObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.*;
import rx.observers.TestObserver;
import scala.Tuple2;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Created by huub on 7-9-15.
 */
public class MultiMapReaderNode extends TimestampedNode {

    private static final Logger log =
            LoggerFactory.getLogger(MultiMapReaderNode.class);

    MergedMap<IPv4Addr, ArpCacheEntry>[] maps;
    KafkaBus<IPv4Addr, ArpCacheEntry>[] busses;
    Subscription[] subscriptions;
    int benchmarkWrites;
    int warmupWrites;
    int tableSize;
    long[][] latencies;
    CountDownLatch warmupLatch;
    CountDownLatch finishedLatch;


    public MultiMapReaderNode(

        MergedMap<IPv4Addr, ArpCacheEntry>[] maps,
        KafkaBus<IPv4Addr, ArpCacheEntry>[] busses,
        int benchmarkWrites,
        int warmupWrites,
        int tableSize

    ) {
        this.maps = maps;
        this.busses = busses;
        this.benchmarkWrites = benchmarkWrites;
        this.warmupWrites = warmupWrites;
        this.tableSize = tableSize;
        this.subscriptions = new Subscription[this.maps.length];
        this.warmupLatch = new CountDownLatch(this.maps.length);
        this.finishedLatch = new CountDownLatch(this.maps.length);
        this.latencies = new long[this.maps.length][benchmarkWrites];

        int totalWarmup = warmupWrites + tableSize;
        int totalExpected = totalWarmup + benchmarkWrites;

        for(int i = 0;i<maps.length;i++) {
            MergedMap currentMap = maps[i];
            ArpMergedMapObserver observer = new ArpMergedMapObserver(
                    this.latencies[i],
                    totalWarmup,
                    totalExpected,
                    finishedLatch,
                    warmupLatch);
            this.subscriptions[i] = maps[i].observable().subscribe(observer);
        }
    }

    @Override
    public void timestampedSetup() {
        try {
            warmupLatch.await();
        } catch (InterruptedException e) {
            log.error("Await on warmupLatch interrupted", e);
        }
    }

    @Override
    public synchronized void timestampedRun() {

        try {
            finishedLatch.await();
        } catch (InterruptedException e) {
            log.error("Await on finishedLatch interrupted", e);
        }
    }

    @Override
    public void shutdown() {
        for(int i = 0;i<maps.length;i++) {
            this.subscriptions[i].unsubscribe();
            this.busses[i].shutdown();
        }
    }

    @Override
    public String postProcessResults(Bookkeeper bookkeeper) {
        super.postProcessResults(bookkeeper);

        Long[] superLatencies = new Long[benchmarkWrites * latencies.length];

        PrintStream rawData = bookkeeper.getFileWriter("raw-latency-data");
        for (int j = 0; j < benchmarkWrites; j++) {
            for (int i = 0; i < latencies.length; i++) {
                rawData.println(latencies[i][j]);
                superLatencies[i*benchmarkWrites + j] = latencies[i][j];
            }
        }
        rawData.close();

        PrintStream output = bookkeeper.getFileWriter("summary");
        output.println("mean=" + StatUtils.mean(Arrays.asList(superLatencies)));
        output.println("stdev=" + StatUtils.standardDeviation(Arrays.asList(superLatencies)));
        output.println("95thpercentile=" + StatUtils.percentile(Arrays.asList(superLatencies), 0.95));
        output.println("99thpercentile=" + StatUtils.percentile(Arrays.asList(superLatencies), 0.99));
        output.println("9999thpercentile=" + StatUtils.percentile(Arrays.asList(superLatencies), 0.9999));
        output.close();

        return "";
    }
}
