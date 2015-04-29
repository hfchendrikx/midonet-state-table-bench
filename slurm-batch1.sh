# ChurnBench to be launched with slurm.
# We will launch at most 21 processes per machine.

# First set of benchmarks to test extremes
processes_a=(11 1601)
size_a=(200 32000)
rate_a=(1 100)

for i in ${processes_a[@]}; do
   for j in ${size_a[@]}; do
       for k in ${rate_a[@]}; do
           ./runChurnSlurm.sh $i $(($i/20 + 1)) $i ARP_TABLE $j $k 
       done
   done
done

# The second set of benchmarks explores the parameter space by varying the number of clients,
# setting the data structure size to 20 times the number of clients, and varying the update
# rate between 1 and 100 updates per second.
processes_b=(101 201 301 401 501 601 701 801 901 1001 1101 1201 1301 1401 1501 1601)
rate_b=(1 10 20 30 40 50 60 70 80 90 100)

for i in ${processes_b[@]}; do
   for j in ${rate_b[@]}; do
       ./runChurnSlurm.sh $i $(($i/20 + 1)) $i ARP_TABLE $(($i * 20)) $j
   done
done
