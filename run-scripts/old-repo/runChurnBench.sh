#!/bin/bash

OMPI_DIR=openmpi/build/install/opt/openMPI
MPI_LIBRARY_PATH=${OMPI_DIR}/lib
BENCH_JAR=benchmarks/build/libs/benchmarks.jar
MPI_JAR=$OMPI_DIR/lib/mpi.jar
CONFIG_FILE=benchmarks/conf/midobench.conf

#For OS-X
export DYLD_LIBRARY_PATH=$DYLD_LIBRARY_PATH:$MPI_LIBRARY_PATH

#The line below should be present in the environment of all machines running the benchmark with MPI
export OPAL_DESTDIR=/Users/Nicolas/IdeaProjects/mido-bench/openmpi/build/install/opt/openMPI

#$1 data structure type, $2 structure type, $3 write count, $4 write rate
java -Dmidobench.config=$CONFIG_FILE -Djava.library.path=$MPI_LIBRARY_PATH -cp $BENCH_JAR:$MPI_JAR org.midonet.benchmarks.ChurnBench $1 $2 $3 $4
exit 0
