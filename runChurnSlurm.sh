# $1 machines, $2 processes, $3 storage type, $4 size, $5 number of writes, $6 write rate
sbatch -N$1-$1 -n$2 -D /home/ubuntu churnslurm.sh $3 $4 $5 $6
