#!/bin/bash
# $1 contains the server's id and $2, $3, $4 contain the 3 server ips.

function join { local IFS="$1"; shift; echo "$*"; }

CLUSTER_CONFIG_DIR=/proj/midonet/lattest/kafka-config
KAFKA_DIR=/usr/share/kafka/kafka_2.10-0.8.2.1
CONFIG_DIR=$KAFKA_DIR/config
KAFKA_BROKER_ID=`expr $1 - 1` #Zookeeper id's start at 1, and broker ids start at 0

PARAMS=("$@")
ZOOKEEPER_NODES=(${PARAMS[@]:1})

for ((i=0; i<${#ZOOKEEPER_NODES[@]}; i++));
do
  ZOOKEEPER_NODES[$i]="${ZOOKEEPER_NODES[i]}:2181"
done
ZOOKEEPER_CONNECTION_STRING=$(IFS=,; echo "${ZOOKEEPER_NODES[*]}")


echo "Broker ID of this kafka node is $KAFKA_BROKER_ID"
echo "Zookeeper servers configured: $ZOOKEEPER_CONNECTION_STRING"

#Copy configuration files
sudo cp $CLUSTER_CONFIG_DIR/server.properties $CONFIG_DIR/server.properties
sudo cp $CLUSTER_CONFIG_DIR/log4j.properties $CONFIG_DIR/log4j.properties
sudo chmod 444 $CONFIG_DIR/server.properties
sudo chmod 444 $CONFIG_DIR/log4j.properties

#Modify server information
sudo sed -i -r 's/zookeeper.connect=.*/zookeeper.connect='"$ZOOKEEPER_CONNECTION_STRING"'/' $CONFIG_DIR/server.properties
sudo sed -i -r "s/broker.id=.*/broker.id=$KAFKA_BROKER_ID/" $CONFIG_DIR/server.properties
sudo sed -i -r "s/advertised.host.name=.*/advertised.host.name=clusternode$1/" $CONFIG_DIR/server.properties

#Set open file limit
ulimit -n 500000

export KAFKA_HEAP_OPTS="-Xmx3G -Xms3G"

sudo JMX_PORT=9996 $KAFKA_DIR/bin/kafka-server-start.sh $CONFIG_DIR/server.properties
