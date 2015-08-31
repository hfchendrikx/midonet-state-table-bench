package org.midonet.benchmarks.latencyNodes;

import org.midonet.cluster.data.storage.ArpMergedMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintStream;
import java.io.PrintWriter;

/**
 * Created by huub on 21-8-15.
 */
public class WriterNode implements TestNode {

    private static final Logger log =
            LoggerFactory.getLogger(WriterNode.class);

    TestWriter writer;

    int writesPerSecond;
    int writeInterval;
    int numberOfWrites;
    int numberOfWarmupWrites;

    long startWarmupTime;
    long endWarmupTime;
    long startBenchmark;
    long endBenchmark;

    /**
     *
     * @param theWriter
     * @param cwritesPerSecond
     * @param cwriteInterval In nanoseconds, (not higher than 999999)
     * @param cnumberOfWrites
     * @param cnumberOfWarmUpWrites
     */
    public WriterNode(TestWriter theWriter, int cwritesPerSecond, int cwriteInterval, int cnumberOfWrites, int cnumberOfWarmUpWrites) {
        this.writer = theWriter;
        this.writesPerSecond = cwritesPerSecond;
        this.writeInterval = cwriteInterval;
        this.numberOfWarmupWrites = cnumberOfWarmUpWrites;
        this.numberOfWrites = cnumberOfWrites;
    }

    @Override
    public void setup() {
        this.startWarmupTime = System.currentTimeMillis();
        this.writer.writeWarmup();
        for (int i=0;i<this.numberOfWarmupWrites;i++) {
            this.writer.writeEntry();
        }
        this.endWarmupTime = System.currentTimeMillis();


    }

    public void shutdown() {

    }

    @Override
    public void run() {
        this.startBenchmark = System.currentTimeMillis();


        long writesPerInterval = Math.round(((this.writesPerSecond / 1000000.0) * this.writeInterval));
        long written = 0;

        log.debug("WritesPerInterval: " + writesPerInterval +
                " numberOfwrites" + numberOfWrites +
                " writeInterval " + writeInterval +
                " DISABLE LOGS DURING BENCHMARKING");

        while (written < numberOfWrites) {

            for (long i=0;i<writesPerInterval;i++) {
                this.writer.writeEntry();
            }

            written += writesPerInterval;

            try {
                Thread.sleep(writeInterval / 1000);
            } catch (InterruptedException e) {
                log.error("Thread sleep interrupted during write run", e);
                return;
            }
        }


        this.endBenchmark = System.currentTimeMillis();
    }

    public String postProcessResults(Bookkeeper bookkeeper) {
        PrintStream logFile = bookkeeper.getFileWriter("timestamps");
        logFile.println("startwarmup="+this.startWarmupTime);
        logFile.println("endwarmup="+this.endWarmupTime);
        logFile.println("startbenchmark="+this.startBenchmark);
        logFile.println("endbenchmark="+this.endBenchmark);
        return "";
    }


}
