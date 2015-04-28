# $1 storage type, $2 size, $3 number of writes, $4 write rate
sbatch -N2 -n2 -D /home/ubuntu churnslurm.sh $1 $2 $3 $4
