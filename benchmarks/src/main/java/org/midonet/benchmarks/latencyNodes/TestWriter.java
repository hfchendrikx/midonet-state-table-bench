package org.midonet.benchmarks.latencyNodes;

/**
 * Created by huub on 24-8-15.
 */
public interface TestWriter {
    public void writeEntry();
    public void writeWarmup();
}
