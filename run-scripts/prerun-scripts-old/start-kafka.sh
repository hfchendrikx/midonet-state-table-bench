#!/bin/bash
#This is executed on the clusternods by the restart-cluster.sh script

KAFKA_DIR=/usr/share/kafka/kafka_2.10-0.8.2.1
CONFIG_DIR=$KAFKA_DIR/config

echo "Running on $(hostname)"
echo "Starting kafka"

ulimit -n 500000
sudo JMX_PORT=9996 $KAFKA_DIR/bin/kafka-server-start.sh -daemon $CONFIG_DIR/server.properties

