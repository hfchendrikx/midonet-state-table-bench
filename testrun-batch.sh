counter=1

run_jvm() {
    mpirun -n 4 java -Dbench.mainDirectory="$PWD" -Dbench.restarts="$counter" -Dzk.hosts=localhost:2181 -Dkafka.hosts=localhost:9092,localhost:9093,localhost:9094 -Dstate-table-bench.config=test.conf -cp ./:benchmarks/build/libs/benchmarks.jar org.midonet.benchmarks.BatchTestBench
}

run_jvm
while [ -f ./restart_jvm ]
do
  counter=$((counter+1))
  run_jvm
done
