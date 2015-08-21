#!/bin/bash

# $1 experiment output file

MAIN_DIR=/proj/midonet/lattest

set -e

echo -e "\n"
echo -e "######################################\n"
echo -e "# installing packages                #\n"
echo -e "######################################\n"
echo -e "\n"

$MAIN_DIR/install-packages-bench.sh

echo -e "awaiting barrier...\n"
/usr/testbed/bin/emulab-sync -n benchbarrier

echo -e "\n"
echo -e "######################################\n"
echo -e "# Starting experiment                #\n"
echo -e "######################################\n"
echo -e "\n"

#clear output file
 > $1

#run test bench
source $MAIN_DIR/testbench.sh > $1

echo -e "\n"
echo -e "######################################\n"
echo -e "# Done                               #\n"
echo -e "######################################\n"
echo -e "\n"
