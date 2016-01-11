mpirun -n 4 java -Dzk.hosts=localhost:2181 -Dstate-table-bench.config=test.conf -cp ./:benchmarks/build/libs/benchmarks.jar org.midonet.benchmarks.MergedMapTestBench
