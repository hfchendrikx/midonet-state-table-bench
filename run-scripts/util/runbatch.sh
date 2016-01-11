#!/bin/bash
#SBATCH --job-name=Statetable-benchmarks
#SBATCH --output=test.out
#SBATCH --error=test.err
#SBATCH --partition=testbench
#SBATCH --nodes=20

# below your job commands:

counter=1 #first run is number 1 (not 0)


run_jvm() {
  mpirun java -Dbench.mainDirectory="$PWD" -Dbench.restarts="$counter" -Djava.net.preferIPv4Stack=true -Dzookeeper.zookeeper_hosts=zoo1:2181,zoo2:2181,zoo3:2181 -cp benchmarks.jar org.midonet.benchmarks.BatchTestBench
}

run_jvm
while [ -f ./restart_jvm ]
do
  sleep 30
  counter=$((counter+1))
  run_jvm
done
