OMPI_DIR=openmpi/build/install/opt/openMPI
MPI_LIBRARY_PATH=${OMPI_DIR}/lib
BENCH_JAR=benchmarks/build/libs/benchmarks.jar
MPI_JAR=$OMPI_DIR/lib/mpi.jar

#For OS-X
export DYLD_LIBRARY_PATH=$DYLD_LIBRARY_PATH:$MPI_LIBRARY_PATH
export OPAL_PREFIX=$OMPI_DIR

#$1 data structure type, $2 structure type, $3 write count, $4 write rate
java -Djava.library.path=$MPI_LIBRARY_PATH -cp $BENCH_JAR:$MPI_JAR org.midonet.benchmarks.ChurnBench benchmarks/conf/midobench.conf $1 $2 $3 $4
