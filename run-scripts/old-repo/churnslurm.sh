MPILIB=/usr/local/lib
OMPIJAR=$OMPILIB/mpi.jar:$OMPILIB/oshmem.jar

MASTERDIR=/home/ubuntu/master
rm -rf $MASTERDIR
mkdir -p $MASTERDIR

export WORKDIR=/home/ubuntu/workdir
srun rm -rf $WORKDIR
srun mkdir -p $WORKDIR

if [ ! -e $TOOLS/expand_nodes.pl ]; then
   scp i007:tools/expand_nodes.pl $TOOLS/expand_nodes.pl
   chmod a+rx $TOOLS/expand_nodes.pl
fi

HOSTS=$($TOOLS/expand_nodes.pl $SLURM_NODELIST 2>/dev/null)

JARDIR=/home/ubuntu/nico-bench/benchmarks/build/libs
JAR=benchmarks.jar
BENCH=org.midonet.benchmarks.ChurnBench
CONFDIR=/home/ubuntu/nico-bench/benchmarks/conf
CONF=midobench.conf

# Setup the Zookeeper nodes
ZK1=`host ${HOSTS[$(($SLURM_NNODES - 1))]} | awk '{print $4}' | tail -n 2 | head -n 1 | echo 192.168.$(cut -f 3,4 -d .)` 
ZK2=`host ${HOSTS[$(($SLURM_NNODES - 2))]} | awk '{print $4}' | tail -n 2 | head -n 1 | echo 192.168.$(cut -f 3,4 -d .)`
ZK3=`host ${HOSTS[$(($SLURM_NNODES - 3))]} | awk '{print $4}' | tail -n 2 | head -n 1 | echo 192.168.$(cut -f 3,4 -d .)`
srun -r $(($SLURM_NNODES - 1)) setupZK.sh 1 $ZK1 $ZK2 $ZK3
srun -r $(($SLURM_NNODES - 2)) setupZK.sh 2 $ZK1 $ZK2 $ZK3
srun -r $(($SLURM_NNODES - 3)) setupZK.sh 3 $ZK1 $ZK2 $ZK3

# Append the ZK nodes to the benchmark configuration file
sed 's/^zookeeper_hosts[[:blank:]]*=.*/zookeeper_hosts='"$ZK1:2181,$ZK2:2181,$ZK3:2181"/ $CONFIGDIR/$CONF > $CONFIGDIR/${CONF}.new
rm -f $CONFIGDIR/$CONF
mv $CONFIGDIR/${CONF}.new $CONFIGDIR/${CONF}

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

