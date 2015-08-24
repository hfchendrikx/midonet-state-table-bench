package org.midonet.benchmarks.latencyNodes;

import org.midonet.cluster.data.storage.ArpMergedMap;

/**
 * Created by huub on 21-8-15.
 */
public class WriterNode implements TestNode {

    TestWriter writer;

    public WriterNode(TestWriter theWriter) {
        this.writer = theWriter;
    }

    @Override
    public void setup() {

    }

    @Override
    public void run() {


        for (int i=0;i<1000;i++) {
            this.writer.writeEntry();
        }
    }
}
