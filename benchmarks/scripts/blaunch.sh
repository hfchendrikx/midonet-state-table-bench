#!/bin/bash

# Copyright 2014 Midokura SARL

# Usage:
# blaunch.sh <benchmarkname> <configfile>

# Description:
# This script launches the benchmark (specified as the fully
# qualified class name) using the given config file, and without
# setting the MPI environment.
# This is intended to be an intermediate step to prevent the actual
# MPI-based script (mpilaunch.sh) to stumble on Midonet dependencies
# Note: this should not be invoked directly: use mpilaunch.sh instead.

MVN_REPO="$HOME/.m2/repository"
SCRIPT_DIR=$(dirname $(readlink -e $0))
BENCH_BASE=$(readlink -e "$SCRIPT_DIR/../..")
BENCH_DIR="$BENCH_BASE/benchmarks"
OMPI_DESTDIR=$(readlink -e "$BENCH_BASE/openmpi/build/install")
OMPI_PREFIX="/opt/openMPI"
OMPI_DIR="${OMPI_DESTDIR}${OMPI_PREFIX}"
MIDO_REPO="$MVN_REPO/org/midonet"
MIDO_VERSION="2.0-SNAPSHOT"
LOGBACKCFG="$BENCH_DIR/target/classes/logback.xml"

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

# The first existing directory is used for JAVA_HOME if needed.
JVM_SEARCH_DIRS="/usr/lib/jvm/java-7-openjdk-amd64 /usr/lib/jvm/java-7-openjdk"

# If JAVA_HOME has not been set, try to determine it.
if [ -z "$JAVA_HOME" ]; then
    # If java is in PATH, use a JAVA_HOME that corresponds to that. This is
    # both consistent with how the upstream startup script works, and how
    # Debian works (read: the use of alternatives to set a system JVM).
    if [ -n "`which java`" ]; then
        java=`which java`
        # Dereference symlink(s)
        while true; do
            if [ -h "$java" ]; then
                java=`readlink "$java"`
                continue
            fi
            break
        done
        JAVA_HOME="`dirname $java`/../"
    # No JAVA_HOME set and no java found in PATH, search for a JVM.
    else
        for jdir in $JVM_SEARCH_DIRS; do
            if [ -x "$jdir/bin/java" ]; then
                JAVA_HOME="$jdir"
                break
            fi
        done
    fi
fi
JAVA="$JAVA_HOME/bin/java"

calculate_heap_sizes()
{
    case "`uname`" in
        Linux)
            system_memory_in_mb=`free -m | awk '/Mem:/ {print $2}'`
            system_cpu_cores=`egrep -c 'processor([[:space:]]+):.*' /proc/cpuinfo`
            break
        ;;
        FreeBSD)
            system_memory_in_bytes=`sysctl hw.physmem | awk '{print $2}'`
            system_memory_in_mb=$((system_memory_in_bytes / 1024 / 1024))
            system_cpu_cores=`sysctl hw.ncpu | awk '{print $2}'`
            break
        ;;
        *)
            # assume reasonable defaults for e.g. a modern desktop or
            # cheap server
            system_memory_in_mb="2048"
            system_cpu_cores="2"
        ;;
    esac
    max_heap_size_in_mb=$((system_memory_in_mb / 2))
    MAX_HEAP_SIZE="${max_heap_size_in_mb}M"

    # Young gen: min(max_sensible_per_modern_cpu_core * num_cores, 1/4 * heap size)
    max_sensible_yg_per_core_in_mb="100"
    max_sensible_yg_in_mb=$((max_sensible_yg_per_core_in_mb * system_cpu_cores))

    desired_yg_in_mb=$((max_heap_size_in_mb / 4))

    if [ "$desired_yg_in_mb" -gt "$max_sensible_yg_in_mb" ]
    then
        HEAP_NEWSIZE="${max_sensible_yg_in_mb}M"
    else
        HEAP_NEWSIZE="${desired_yg_in_mb}M"
    fi
}

if [ -z "$MIDO_BOOTSTRAP_JAR" ]; then
    MIDO_BOOTSTRAP_JAR="$MIDO_REPO/midonet-jdk-bootstrap/$MIDO_VERSION/midonet-jdk-bootstrap-${MIDO_VERSION}.jar"
fi

if [ ! -e "$MIDO_BOOTSTRAP_JAR" ]; then
   echo "ERROR: $MIDO_BOOTSTRAP_JAR does not exist"
   exit 1
fi

if [ -z "$MIDO_JAR" ]; then
    MIDO_JAR="$MIDO_REPO/midolman/$MIDO_VERSION/midolman-${MIDO_VERSION}.jar"
fi

if [ ! -e "$MIDO_JAR" ]; then
   echo "ERROR: $MIDO_JAR does not exist"
   exit 1
fi

JAVA_LIBRARY_PATH=-Djava.library.path=/lib:/usr/lib:${OMPI_DIR}/lib

# Hack to get the list of dependencies from the midolman debian package
MIDO_DEB="$MIDO_REPO/midolman/$MIDO_VERSION/midolman-${MIDO_VERSION}.deb"
MIDO_DEPS=$(dpkg -c "$MIDO_DEB" | \
            sed -e 's/.* //' | \
            grep '\.\/usr\/share\/midolman\/dep\/.*\.jar' | \
            sed -e 's/.*\///' )

MIDO_DEP_CLASS_PATH=""
for d in $MIDO_DEPS; do
    MIDO_DEP_CLASS_PATH="${MIDO_DEP_CLASS_PATH}$(find $MVN_REPO -name ${d}):"
done
MIDO_DEP_CLASS_PATH="${MIDO_DEP_CLASS_PATH}${OMPI_DIR}/lib/mpi.jar"

if [ -n "$LOGBACKCFG" -a -f "$LOGBACKCFG" ]; then
    LOGCFG="-Dlogback.configurationFile=file:$LOGBACKCFG"
fi

# Set the local install path for OpenMPI
export OPAL_DESTDIR="$OMPI_DESTDIR"

# Set the dependencies in the CLASSPATH variables (it is too long for the
# the command line and is truncated when passing it to java via '-cp'
export CLASSPATH="${CLASSPATH}:${MIDO_DEP_CLASS_PATH}:${MIDO_JAR}:${MIDO_BENCHMARKS}"
CMD="$JAVA  $LOGCFG $JAVA_LIBRARY_PATH -Xbootclasspath/p:$MIDO_BOOTSTRAP_JAR  $BENCHMARK --config $CONFIG"
echo $CMD $@
$CMD $@

