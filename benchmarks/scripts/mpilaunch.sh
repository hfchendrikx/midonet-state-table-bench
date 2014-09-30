#!/bin/bash

# Copyright 2014 Midokura SARL

# Usage:
# mpilaunch.sh <benchmarkname> <configfile>

# Description:
# This script runs a benchmark (specified as the fully-qualified
# class name) with the given configuration, using the MPI parallel
# environment. The MPI parameters are extracted from the configuration
# file.

SCRIPT_DIR=$(dirname $(readlink -e $0))
BENCH_BASE=$(readlink -e "$SCRIPT_DIR/../..")
BENCH_DIR="$BENCH_BASE/benchmarks"
OMPI_DESTDIR=$(readlink -e "$BENCH_BASE/openmpi/build/install")
OMPI_PREFIX="/opt/openMPI"
OMPI_DIR="${OMPI_DESTDIR}${OMPI_PREFIX}"

if [ -z "$MIDO_BENCHMARKS" ]; then
    MIDO_BENCHMARKS=$(readlink -m $(ls $BENCH_DIR/target/benchmarks-*.jar))
fi

if [ -z "$MIDO_BENCHMARKS" -o ! -e "$MIDO_BENCHMARKS" ]; then
   echo "ERROR: invalid MIDO_BENCHMARKS classpath ($MIDO_BENCHMARKS)"
   exit 1
fi

BENCHMARK="$1"
shift
CONFIG="$1"
shift

if [ -z "$BENCHMARK" ]; then
    echo "ERROR: usage: $0 <benchmark_clas> <config_file>"
    exit 1
fi

if [ -z "$CONFIG" -o ! -f "$CONFIG" ]; then
    echo "ERROR: invalid configuration file ($CONFIG)"
    echo "ERROR: usage: $0 <benchmark_clas> <config_file>"
    exit 1
fi
CONFIG=$(readlink -e $CONFIG)

# Get mpi configuration from config file
OMPI_HOSTS=$(grep '^\s*mpi_hosts\s*=' $CONFIG | \
             cut -f 2 -d = | \
             sed -e 's/^[[:blank:]]*//' | \
             sed -e 's/[[:blank:]].*//')
if [ -z "$OMPI_HOSTS" ]; then
    OMPI_HOSTS="127.0.0.1"
fi

export OPAL_DESTDIR="$OMPI_DESTDIR"
CMD="$OMPI_DIR/bin/mpirun -prefix $OMPI_DIR --bind-to none -host $OMPI_HOSTS $SCRIPT_DIR/blaunch.sh $BENCHMARK $CONFIG"
echo $CMD $@
$CMD $@

