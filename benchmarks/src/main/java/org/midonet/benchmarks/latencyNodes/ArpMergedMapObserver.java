package org.midonet.benchmarks.latencyNodes;

import org.midonet.benchmarks.mpi.MPIBenchApp;
import org.midonet.midolman.state.ArpCacheEntry;
import org.midonet.packets.IPv4Addr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observer;
import scala.Tuple2;

import java.util.concurrent.CountDownLatch;

/**
 * Created by huub on 7-9-15.
 */
public class ArpMergedMapObserver implements Observer<Tuple2<IPv4Addr, ArpCacheEntry>> {

    private static final Logger log =
            LoggerFactory.getLogger(ArpMergedMapObserver.class);

    int offset = 0;
    int totalWarmup = 0;
    int totalExpected = 0;
    CountDownLatch doneLatch;
    boolean done = false;
    CountDownLatch warmupLatch;
    boolean warmedUp = false;
    long[] latencies;


    public ArpMergedMapObserver(long[] latencies, int totalWarmup, int totalExpected, CountDownLatch doneLatch, CountDownLatch warmupLatch) {
        this.latencies = latencies;
        this.totalWarmup = totalWarmup;
        this.totalExpected = totalExpected;
        this.warmupLatch = warmupLatch;
        this.doneLatch = doneLatch;
    }

    @Override
    public void onCompleted() {

    }

    @Override
    public void onError(Throwable e) {

    }

    public boolean isDone() {
        return done;
    }

    @Override
    public void onNext(Tuple2<IPv4Addr, ArpCacheEntry> iPv4AddrArpCacheEntryTuple2) {

        if(offset >= totalWarmup) {
            long sent = ArpMergedMapTest.getTimeFromEntry(iPv4AddrArpCacheEntryTuple2._2());
            latencies[offset-totalWarmup] = MPIBenchApp.getTime() - sent;
        }

        offset++;

        if(offset >= totalExpected && !done) {
            log.debug("Reader done");
            done = true;
            doneLatch.countDown();
        }

        if (!warmedUp) {
            log.debug("Reader warmed up");
            warmedUp = true;
            warmupLatch.countDown();
        }

    }
}
