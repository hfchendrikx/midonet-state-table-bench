package org.midonet.benchmarks.latencyNodes;

import org.midonet.benchmarks.StatUtils;

import java.awt.print.Book;
import java.io.PrintStream;
import java.io.StringWriter;
import java.util.Arrays;

/**
 * Created by huub on 21-8-15.
 */
public class ReaderNode implements TestNode {

    protected TestReader reader;
    protected int numberOfReads = 0;
    protected int numberOfWarmupReads = 0;
    Long[] latencies;

    public ReaderNode(TestReader theReader, int cNumberOfReads, int cNumberOfWarmupReads) {
        this.reader = theReader;
        this.numberOfReads = cNumberOfReads;
        this.numberOfWarmupReads = cNumberOfWarmupReads;
        this.latencies = new Long[numberOfReads];
    }

    @Override
    public void setup() {
        this.reader.readWarmup();
        for (int i=0;i<numberOfWarmupReads;i++) {
            this.reader.readEntry();
        }
    }

    @Override
    public void run() {
        for (int i =0; i< numberOfReads;i++) {
            latencies[i] = this.reader.readEntry();
        }
    }

    public String postProcessResults(Bookkeeper bookkeeper) {
        PrintStream rawData = bookkeeper.getFileWriter("raw-latency-data");
        for (int i=0;i<latencies.length;i++) {
            rawData.println(latencies[i]);
        }
        rawData.close();

        PrintStream output = bookkeeper.getFileWriter("summary");
        output.println("mean=" + StatUtils.mean(Arrays.asList(latencies)));
        output.println("stdev=" + StatUtils.standardDeviation(Arrays.asList(latencies)));
        output.println("95thpercentile=" + StatUtils.percentile(Arrays.asList(latencies), 0.95));
        output.println("99thpercentile=" + StatUtils.percentile(Arrays.asList(latencies), 0.99));
        output.println("9999thpercentile=" + StatUtils.percentile(Arrays.asList(latencies), 0.9999));
        output.close();

        return "";
    }
}
