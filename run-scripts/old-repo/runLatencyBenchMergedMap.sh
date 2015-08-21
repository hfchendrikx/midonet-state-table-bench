#!/bin/bash
BASE_DIR=`pwd`
OMPI_DIR=${BASE_DIR}/openmpi/build/install/opt/openMPI
MPI_LIBRARY_PATH=${OMPI_DIR}/lib
BENCH_JAR=${BASE_DIR}/benchmarks/build/libs/benchmarks.jar
MPI_JAR=${OMPI_DIR}/lib/mpi.jar
MPI_DESTDIR=${BASE_DIR}/openmpi/build/install
CONFIG_FILE=benchmarks/conf/midobench.conf

#For OS-X
export DYLD_LIBRARY_PATH=${DYLD_LIBRARY_PATH}:${MPI_LIBRARY_PATH}

#For Linux
export LD_LIBRARY_PATH=${LD_LIBRARY_PATH}:${MPI_LIBRARY_PATH}

#$1 data structure type, $2 data structure size, $3 write count
java -Dmidobench.config=$CONFIG_FILE -Dzk.hosts="192.168.0.18:2181" -Dkafka.hosts="192.168.0.18:9092" -cp .:${BENCH_JAR}:${MPI_JAR} org.midonet.benchmarks.LatencyBench $1 $2 $3
exit 0
