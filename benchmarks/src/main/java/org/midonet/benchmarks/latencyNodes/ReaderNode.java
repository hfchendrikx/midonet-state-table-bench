package org.midonet.benchmarks.latencyNodes;

import org.midonet.benchmarks.StatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.print.Book;
import java.io.PrintStream;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.concurrent.TimeoutException;

/**
 * Created by huub on 21-8-15.
 */
public class ReaderNode implements TestNode {

    private static final Logger log =
            LoggerFactory.getLogger(ReaderNode.class);
    private static final Logger status_log =
            LoggerFactory.getLogger("status");


    protected TestReader reader;
    protected int numberOfReads = 0;
    protected int numberOfWarmupReads = 0;
    protected int totalRead = 0;
    Long[] latencies;
    long[] timestamps;

    long startWarmupTime;
    long endWarmupTime;
    long startBenchmark;
    long endBenchmark;

    public ReaderNode(TestReader theReader, int cNumberOfReads, int cNumberOfWarmupReads) {
        this.reader = theReader;
        this.numberOfReads = cNumberOfReads;
        this.numberOfWarmupReads = cNumberOfWarmupReads;
        this.latencies = new Long[numberOfReads];
        this.timestamps = new long[numberOfReads];
    }

    @Override
    public void setup() {
        this.startWarmupTime = System.currentTimeMillis();
        this.reader.readWarmup();
        for (int i=0;i<numberOfWarmupReads;i++) {
            this.reader.readEntry();
        }
        this.endWarmupTime = System.currentTimeMillis();
    }

    @Override
    public void run() {

        int numberOfExceptions = 0;
        this.startBenchmark = System.currentTimeMillis();

        while (totalRead < numberOfReads) {
            try {
                latencies[totalRead] = this.reader.readEntry();
                timestamps[totalRead] = System.currentTimeMillis();
                totalRead++;
            } catch (Exception e) {
                status_log.info("Exception at reader: " + e.getClass().toString());
                numberOfExceptions++;

                if (numberOfExceptions > 20) {
                    log.error("Caught 20 exceptions, stopping now", e);
                    this.endBenchmark = System.currentTimeMillis();
                    throw e;
                }
            }
        }

        this.endBenchmark = System.currentTimeMillis();
    }

    public void shutdown() {

    }

    public String postProcessResults(Bookkeeper bookkeeper) {
        PrintStream rawData = bookkeeper.getFileWriter("raw-latency-data");
        for (int i=0;i<totalRead;i++) {
            rawData.println(latencies[i]);
        }
        rawData.close();

        PrintStream rawTimestamps = bookkeeper.getFileWriter("raw-timestamp-data");
        for (int i=0;i<totalRead;i++) {
            rawTimestamps.println(timestamps[i]);
        }
        rawTimestamps.close();


        Long[] receivedLatencies = Arrays.copyOfRange(latencies, 0, totalRead);

        PrintStream output = bookkeeper.getFileWriter("summary");
        output.println("mean=" + StatUtils.mean(Arrays.asList(receivedLatencies)));
        output.println("stdev=" + StatUtils.standardDeviation(Arrays.asList(receivedLatencies)));
        output.println("95thpercentile=" + StatUtils.percentile(Arrays.asList(receivedLatencies), 0.95));
        output.println("99thpercentile=" + StatUtils.percentile(Arrays.asList(receivedLatencies), 0.99));
        output.println("9999thpercentile=" + StatUtils.percentile(Arrays.asList(receivedLatencies), 0.9999));
        //output.println("received=" + StatUtils.percentile(Arrays.asList(receivedLatencies), 0.9999));
        output.close();

        PrintStream logFile = bookkeeper.getFileWriter("timestamps");
        logFile.println("startwarmup="+this.startWarmupTime);
        logFile.println("endwarmup="+this.endWarmupTime);
        logFile.println("startbenchmark="+this.startBenchmark);
        logFile.println("endbenchmark=" + this.endBenchmark);

        return "";
    }
}
