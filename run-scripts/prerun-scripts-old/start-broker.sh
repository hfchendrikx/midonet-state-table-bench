#!/bin/bash
#This is executed on the clusternods by the restart-cluster.sh script

sleep $(( ( RANDOM % 10 )  + 1 ))

KAFKA_DIR=/usr/share/kafka/kafka_2.10-0.8.2.1
CONFIG_DIR=$KAFKA_DIR/config
RUN_DIR=/opt/broker
LOG_DIR=~/broker-logs
LOG_DIR_PATH=$(readlink -f $LOG_DIR)

echo "Taking log config files from: $LOG_DIR_PATH"
echo "Configuring zookeeperhosts with mn-conf..."
echo "zookeeper.zookeeper_hosts:\"zoo1:2181,zoo2:2181,zoo3:2181\"" | MIDO_ZOOKEEPER_HOSTS="zoo1:2181,zoo2:2181,zoo3:2181" mn-conf set -t default  
#echo "jgroups.heartbeat_timeout:3000s" | MIDO_ZOOKEEPER_HOSTS="zoo1:2181,zoo2:2181,zoo3:2181" mn-conf set -t default
#echo "jgroups.heartbeat_period:3000s" | MIDO_ZOOKEEPER_HOSTS="zoo1:2181,zoo2:2181,zoo3:2181" mn-conf set -t default

echo "Running on $(hostname)"
IFS='.' read -a HOSTNAME_PARTS <<< $(hostname)
HOST=${HOSTNAME_PARTS[0]}
echo "Client bind host: $HOST"
echo "Starting broker"
LOG_FILE="$LOG_DIR/$HOST.log"

ulimit -n 500000
nohup java -Xmx6G -Xms6G -XX:+UseG1GC -Djava.net.preferIPv4Stack=true -Dzookeeper.zookeeper_hosts=zoo1:2181,zoo2:2181,zoo3:2181 -Dcom.sun.management.jmxremote.port=9996 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -Dlogback.configurationFile=$LOG_DIR_PATH/logback.xml -Dlog4j.configurationFile=$LOG_DIR_PATH/log4j2.xml -cp $RUN_DIR:$RUN_DIR/midonet-cluster-5.1-SNAPSHOT-all.jar org.midonet.cluster.data.storage.jgroups.JGroupsBroker 12345 $HOST </dev/null > $LOG_FILE 2>&1 &

