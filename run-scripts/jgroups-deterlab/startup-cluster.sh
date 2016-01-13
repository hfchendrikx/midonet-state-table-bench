#!/bin/bash
# $1 contains the number of testbench nodes
# $2 contains the number of cluster nodes
# $3 use oracle jvm
# $4 start zookeeper on this node
# $5 start kafka on this node
# $6 Zookeeper ID of this node
# $7 Kafka ID of this node
# $8 Init ssd
# $9...n (rest are the addresses of the zookeeper nodes)

#Load settings
SCRIPT_DIR=$( cd $( dirname "${BASH_SOURCE[0]}" ) && pwd )
. $SCRIPT_DIR/settings.sh

PARAMS=("$@")
NR_TESTBENCH_NODES=$1
NR_CLUSTER_NODES=$2
USE_ORACLE_JVM=$3
START_ZOOKEEPER=$4
START_KAFKA=$5
ZOOKEEPER_SERVER_ID=$6
KAFKA_SERVER_ID=$7
INIT_SSD=$8
ZOOKEEPER_NODES=(${PARAMS[@]:8})


CLUSTERNODES_AT_BARRIER=`expr $NR_CLUSTER_NODES - 1`

ulimit -a

set -e

cd $SETUP_ROOT_DIR

echo -e "\n"
echo -e "######################################\n"
echo -e "# installing packages                #\n"
echo -e "######################################\n"
echo -e "\n"

#Arguments: Use oracle jvm (1 or 0), Install zookeeper (1 or 0)
$SETUP_ROOT_DIR/install-packages-cluster.sh $USE_ORACLE_JVM $START_ZOOKEEPER

if [ $START_ZOOKEEPER -eq 1 ]; then
  echo -e "\n"
  echo -e "######################################\n"
  echo -e "# Starting zookeeper                 #\n"
  echo -e "######################################\n"
  echo -e "\n"

  $SETUP_ROOT_DIR/setupZK.sh $ZOOKEEPER_SERVER_ID "${ZOOKEEPER_NODES[@]}"
fi

if [ $INIT_SSD -eq 1 ]; then
  echo -e "SETTING UP SSD..."
  echo -e "\n\n\n...............SETTING UP SSD DISABLED (in startup-cluster.sh)............\n\n\n"
  #$SETUP_ROOT_DIR/setup-ssd.sh
fi

if [ "$ZOOKEEPER_SERVER_ID" = "1" ]; then
	echo -e "Creating and awaiting barrier...\n"
       /usr/testbed/bin/emulab-sync -i $CLUSTERNODES_AT_BARRIER -n clusterbarrier
else
	echo -e "awaiting barrier...\n"
       /usr/testbed/bin/emulab-sync -n clusterbarrier
fi

if [ $START_KAFKA -eq 1 ]; then
  echo -e "\n"
  echo -e "######################################\n"
  echo -e "# Starting Broker                    #\n"
  echo -e "######################################\n"
  echo -e "\n"

  sudo bash $SETUP_ROOT_DIR/startBroker.sh $KAFKA_SERVER_ID
fi

echo -e "\n"
echo -e "Running nmon, logging to /var/log/nmon..."
sudo rm -f -r /var/log/nmon
sudo mkdir /var/log/nmon
sudo chmod 777 /var/log/nmon
nmon -f -s 10 -c 10000000 -m /var/log/nmon 

if [ "$ZOOKEEPER_SERVER_ID" = "1" ]; then
	echo -e "Creating and awaiting barrier for $NR_TESTBENCH_NODES bench nodes...\n"
       /usr/testbed/bin/emulab-sync -i $NR_TESTBENCH_NODES -n benchbarrier
fi
