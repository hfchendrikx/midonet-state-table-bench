#!/bin/bash

export PDSH_SSH_ARGS_APPEND="-q -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null"


MAIN_DIR=~
PRERUN_DIR=$MAIN_DIR/prerun-scripts

cd $MAIN_DIR

$MAIN_DIR/pdsh-cluster bash $PRERUN_DIR/stop-broker.sh
$MAIN_DIR/pdsh-zoo bash $PRERUN_DIR/stop-zookeeper.sh
#$MAIN_DIR/pdsh-cluster bash $PRERUN_DIR/clear-kafka.sh
$MAIN_DIR/pdsh-zoo bash $PRERUN_DIR/clear-zookeeper.sh
$MAIN_DIR/pdsh-zoo bash $PRERUN_DIR/start-zookeeper.sh
$MAIN_DIR/pdsh-cluster bash $PRERUN_DIR/start-broker.sh

echo "Going to sleep for 60 seconds..."
sleep 60

echo "Done, hopefully the cluster is running again"
