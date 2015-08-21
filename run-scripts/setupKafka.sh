#!/bin/bash
# $1 contains the server's id and $2, $3, $4 contain the 3 server ips.

KAFKA_DIR=/usr/share/kafka/kafka_2.10-0.8.2.1
CONFIG_DIR=$KAFKA_DIR/config
KAFKA_BROKER_ID=`expr $1 - 1` #Zookeeper id's start at 1, and broker ids start at 0

sudo sed -i -r 's/zookeeper.connect=.*/zookeeper.connect=clusternode1:2181,clusternode2:2181,clusternode3:2181/' $CONFIG_DIR/server.properties
sudo sed -i -r "s/broker.id=.*/broker.id=$KAFKA_BROKER_ID/" $CONFIG_DIR/server.properties

sudo $KAFKA_DIR/bin/kafka-server-start.sh $CONFIG_DIR/server.properties
