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
export OPAL_PREFIX="${OMPI_DIR}"

#For Linux
export LD_LIBRARY_PATH=${LD_LIBRARY_PATH}:${MPI_LIBRARY_PATH}

#export OPAL_DESTDIR="${MPI_DESTDIR}"

#$1 data structure type, $2 data structure size, $3 write count
java -Dmidobench.conf=$CONFIG_FILE -Djute.maxbuffer=50M -Djava.library.path=${MPI_LIBRARY_PATH} -cp .:${BENCH_JAR}:${MPI_JAR} org.midonet.benchmarks.LatencyBench benchmarks/conf/midobench.conf $1 $2 $3
exit 0
