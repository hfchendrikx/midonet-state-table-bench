package org.midonet.benchmarks.latencyNodes;

/**
 * Created by huub on 21-8-15.
 */
public class WriterNode implements TestNode {

    @Override
    public void setup() {
        try {
            Thread.sleep(4000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {

    }
}
