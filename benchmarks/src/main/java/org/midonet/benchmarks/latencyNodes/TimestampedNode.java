package org.midonet.benchmarks.latencyNodes;

import mpi.MPI;

import java.io.PrintStream;

/**
 * Created by huub on 7-9-15.
 */
public abstract class TimestampedNode implements TestNode {

    long startWarmupTime;
    long endWarmupTime;
    long startBenchmark;
    long endBenchmark;

    @Override
    public void setup() {
        this.startWarmupTime = System.currentTimeMillis();
        this.timestampedSetup();
        this.endWarmupTime = System.currentTimeMillis();
    }

    protected abstract void timestampedSetup();

    @Override
    public void run() {
        this.startBenchmark = System.currentTimeMillis();
        this.timestampedRun();
        this.endBenchmark = System.currentTimeMillis();
    }

    protected abstract void timestampedRun();

    public String postProcessResults(Bookkeeper bookkeeper) {
        PrintStream logFile = bookkeeper.getFileWriter("timestamps");
        logFile.println("startwarmup="+this.startWarmupTime);
        logFile.println("endwarmup="+this.endWarmupTime);
        logFile.println("startbenchmark="+this.startBenchmark);
        logFile.println("endbenchmark="+this.endBenchmark);
        return "";
    }
}
