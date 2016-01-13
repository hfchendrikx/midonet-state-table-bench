#!/bin/bash

#Load settings
SCRIPT_DIR=$( cd $( dirname "${BASH_SOURCE[0]}" ) && pwd )
. $SCRIPT_DIR/../settings.sh

export PDSH_SSH_ARGS_APPEND="-q -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null"

PRERUN_DIR=$SETUP_ROOT_DIR/prerun-scripts

cd $SETUP_ROOT_DIR

$UTIL_DIR/pdsh-cluster bash $PRERUN_DIR/stop-broker.sh
$UTIL_DIR/pdsh-zoo bash $PRERUN_DIR/stop-zookeeper.sh
#$UTIL_DIR/pdsh-cluster bash $PRERUN_DIR/clear-kafka.sh
$UTIL_DIR/pdsh-zoo bash $PRERUN_DIR/clear-zookeeper.sh
$UTIL_DIR/pdsh-zoo bash $PRERUN_DIR/start-zookeeper.sh
$UTIL_DIR/pdsh-cluster bash $PRERUN_DIR/start-broker.sh

echo "Going to sleep for 60 seconds..."
sleep 60

echo "Done, hopefully the cluster is running again"
