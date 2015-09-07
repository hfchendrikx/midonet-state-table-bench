mpirun -n 4 java -Dzk.hosts=localhost:2181 -Dkafka.hosts=localhost:9092,localhost:9093,localhost:9094 -cp ./:benchmarks/build/libs/benchmarks.jar org.midonet.benchmarks.MultiMergedMapTestBench
