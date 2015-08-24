package org.midonet.benchmarks.latencyNodes;

/**
 * Created by huub on 21-8-15.
 */
public class ReaderNode implements TestNode {

    protected TestReader reader;

    public ReaderNode(TestReader theReader) {
        this.reader = theReader;
    }

    @Override
    public void setup() {

    }

    @Override
    public void run() {

        long[] latencies = new long[1000];
        for (int i =0; i< 1000;i++) {
            latencies[i] = this.reader.readEntry();
        }
        for (int i =0; i< 1000;i++) {
            System.out.println("l: " + latencies[i] / 1000.0 + " ms");
        }
    }
}
