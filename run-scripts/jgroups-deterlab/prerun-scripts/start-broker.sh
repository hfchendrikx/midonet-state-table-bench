#!/bin/bash
#This is executed on the clusternods by the restart-cluster.sh script

sleep $(( ( RANDOM % 10 )  + 1 ))

#Load settings
SCRIPT_DIR=$( cd $( dirname "${BASH_SOURCE[0]}" ) && pwd )
. $SCRIPT_DIR/../settings.sh

RUN_DIR=/opt/broker

echo "Configuring zookeeperhosts with mn-conf..."
echo "zookeeper.zookeeper_hosts:\"zoo1:2181,zoo2:2181,zoo3:2181\"" | MIDO_ZOOKEEPER_HOSTS="zoo1:2181,zoo2:2181,zoo3:2181" mn-conf set -t default  
#echo "jgroups.heartbeat_timeout:3000s" | MIDO_ZOOKEEPER_HOSTS="zoo1:2181,zoo2:2181,zoo3:2181" mn-conf set -t default
#echo "jgroups.heartbeat_period:3000s" | MIDO_ZOOKEEPER_HOSTS="zoo1:2181,zoo2:2181,zoo3:2181" mn-conf set -t default

echo "Running on $(hostname)"
IFS='.' read -a HOSTNAME_PARTS <<< $(hostname)
HOST=${HOSTNAME_PARTS[0]}
echo "Client bind host: $HOST"
echo "Starting broker"
LOG_FILE="$BROKER_STDOUT_LOCATION/$HOST.log"

ulimit -n 500000
nohup java -Xmx6G -Xms6G -XX:+UseG1GC -Djava.net.preferIPv4Stack=true -Dzookeeper.zookeeper_hosts=zoo1:2181,zoo2:2181,zoo3:2181 -Dcom.sun.management.jmxremote.port=9996 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -Dlogback.configurationFile=$BROKER_LOGBACK_CONFIG -Dlog4j.configurationFile=$BROKER_LOG4J2_CONFIG -cp $RUN_DIR:$RUN_DIR/midonet-cluster-5.1-SNAPSHOT-all.jar org.midonet.cluster.data.storage.jgroups.JGroupsBroker 12345 $HOST </dev/null > $LOG_FILE 2>&1 &
