#!/bin/bash
#This is executed on the clusternods by the restart-cluster.sh script
ZK_DIR=/usr/share/zookeeper

echo "Running on $(hostname)"
echo "Starting jmx"
/proj/midonet/lattest/jmx/start-jmx-daemon.sh
