package org.midonet.benchmarks.latencyNodes;

import org.midonet.benchmarks.StatUtils;

import java.util.Arrays;

/**
 * Created by huub on 21-8-15.
 */
public class ReaderNode implements TestNode {

    protected TestReader reader;
    Long[] latencies = new Long[1000];

    public ReaderNode(TestReader theReader) {
        this.reader = theReader;
    }

    @Override
    public void setup() {

    }

    @Override
    public void run() {
        for (int i =0; i< 1000;i++) {
            latencies[i] = this.reader.readEntry();
        }
    }

    public void postProcessResults() {
        System.out.println("Mean: " + StatUtils.mean(Arrays.asList(latencies)));
        System.out.println("Std. Deviation: " + StatUtils.standardDeviation(Arrays.asList(latencies)));
        System.out.println("95th percentile: " + StatUtils.percentile(Arrays.asList(latencies), 0.95));
    }
}
