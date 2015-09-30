#!/bin/bash
BASE_DIR=`pwd`
OMPI_DIR=${BASE_DIR}/usr/share/openmpi/build
MPI_LIBRARY_PATH=${OMPI_DIR}/lib
BENCH_JAR=${BASE_DIR}/benchmarks.jar
MPI_JAR=${OMPI_DIR}/lib/mpi.jar
CONFIG_FILE=${BASE_DIR}/midobench.conf

#For OS-X
export DYLD_LIBRARY_PATH=${DYLD_LIBRARY_PATH}:${MPI_LIBRARY_PATH}

#For Linux
export LD_LIBRARY_PATH=${LD_LIBRARY_PATH}:${MPI_LIBRARY_PATH}

#$1 data structure type, $2 data structure size, $3 write count
java -Dmidobench.config=$CONFIG_FILE -Dzk.hosts="clusternode1:2181,clusternode2:2181,clusternode3:2181" -Dkafka.hosts="clusternode1:9092,clusternode2:9092,clusternode3:9092" -cp .:${BENCH_JAR}:${MPI_JAR} org.midonet.benchmarks.LatencyBench $1 $2 $3
exit 0
