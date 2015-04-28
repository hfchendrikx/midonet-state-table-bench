MPILIB=/usr/local/lib
OMPIJAR=$OMPILIB/mpi.jar:$OMPILIB/oshmem.jar

MASTERDIR=/home/ubuntu/master
rm -rf $MASTERDIR
mkdir -p $MASTERDIR

export WORKDIR=/home/ubuntu/workdir
srun rm -rf $WORKDIR
srun mkdir -p $WORKDIR

JARDIR=/home/ubuntu/nico-bench/benchmarks/build/libs
JAR=benchmarks.jar
BENCH=org.midonet.benchmarks.ChurnBench
CONFDIR=/home/ubuntu/nico-bench/benchmarks/conf
CONF=midobench.conf

# Distribute the app and the configuration.
# (Note theat scp may fail depending on the configuration - probably
# it should be set to automatically accept connections to unknown hosts)
scp ubuntu@mpi-slurm-master:$JARDIR/${JAR} $MASTERDIR/
scp ubuntu@mpi-slurm-master:$CONFDIR/$CONF $MASTERDIR
sbcast -f $MASTERDIR/$JAR $WORKDIR/$JAR
sbcast -f $MASTERDIR/$CONF $WORKDIR/$CONF

# Execute parallel app ($1 storage type, $2 size, $3 number of writes, $4 write rate
mpirun -wdir $WORKDIR java -Dmidobench.config=$WORKDIR/$CONF -Djava.library.path=$OMPILIB -cp $OMPIJAR:$WORKDIR:$JAR $BENCH $1 $2 $3 $4

# (Send back to master whatever results/files are interesting)
# Nothing to do here for now.
exit 0

