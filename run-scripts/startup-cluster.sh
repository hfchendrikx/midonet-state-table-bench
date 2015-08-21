#!/bin/bash
# $1 contains the number of testbench nodes
# $2 contains the number of cluster nodes
# $3 contains the server's id and $4, $5, $6 contain the 3 server ips.

MAIN_DIR=/proj/midonet/lattest

CLUSTERNODES_AT_BARRIER=`expr $2 - 1`

set -e

echo -e "\n"
echo -e "######################################\n"
echo -e "# installing packages                #\n"
echo -e "######################################\n"
echo -e "\n"

$MAIN_DIR/install-packages-cluster.sh

echo -e "\n"
echo -e "######################################\n"
echo -e "# Starting zookeeper                 #\n"
echo -e "######################################\n"
echo -e "\n"

$MAIN_DIR/setupZK.sh $3 $4 $5 $6

if [ "$3" = "1" ]; then
	echo -e "Creating and awaiting barrier...\n"
       /usr/testbed/bin/emulab-sync -i $CLUSTERNODES_AT_BARRIER -n clusterbarrier
else
	echo -e "awaiting barrier...\n"
       /usr/testbed/bin/emulab-sync -n clusterbarrier
fi

echo -e "\n"
echo -e "######################################\n"
echo -e "# Starting kafka                     #\n"
echo -e "######################################\n"
echo -e "\n"

$MAIN_DIR/setupKafka.sh $3 $4 $5 $6 &

if [ "$3" = "1" ]; then
	echo -e "Creating and awaiting barrier...\n"
       /usr/testbed/bin/emulab-sync -i $1 -n benchbarrier
fi
