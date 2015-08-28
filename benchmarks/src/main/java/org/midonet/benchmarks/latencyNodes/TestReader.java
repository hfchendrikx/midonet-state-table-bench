package org.midonet.benchmarks.latencyNodes;

/**
 * Created by huub on 24-8-15.
 */
public interface TestReader {
    /**
     * @return Returns nanosecond latency (int can store max latency of about 2000 seconds)
     */
    long readEntry();
    void readWarmup();
}
