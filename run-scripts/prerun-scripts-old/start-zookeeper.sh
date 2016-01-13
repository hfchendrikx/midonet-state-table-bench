#!/bin/bash
#This is executed on the clusternods by the restart-cluster.sh script
ZK_DIR=/usr/share/zookeeper

echo "Running on $(hostname)"
echo "Starting zookeeper"
sudo JVMFLAGS="-Xmx2048m -Dcom.sun.management.jmxremote.port=9998 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false" $ZK_DIR/bin/zkServer.sh start

