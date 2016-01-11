#!/bin/bash
#SBATCH --job-name=Statetable-benchmarks
#SBATCH --output=test.out
#SBATCH --error=test.err
#SBATCH --partition=testbench
#SBATCH --nodes=15

# below your job commands:
mpirun java -Dkafka.hosts=clusternode1:9092,clusternode2:9092,clusternode3:9092 -Dzk.hosts=clusternode1:2181,clusternode2:2181,clusternode3:2181 -cp benchmarks.jar org.midonet.benchmarks.MultiMergedMapTestBench 
