#!/bin/bash
BASE_DIR=/home/ubuntu/bench/midokura-benchmarks
MPI_RUN=$BASE_DIR/openmpi/build/mpirun_wrapper.sh
HOSTS="192.168.0.8,192.168.0.15"

# $1 is the storage type, $2 is the data structure size, and $3 is the number of writes
# to perform, and $4 is the number of writes per second. 
mpirun -host $HOSTS $BASE_DIR/runChurnBench.sh $1 $2 $3 $4
