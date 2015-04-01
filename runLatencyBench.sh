# Runs the latency benchmark for the given data structure (stored in $1) and for sizes comprised between
# $2 and $3 in increments of $4.
size=$2

while [ $size -le "$3" ]
do
  ./runMapSetBench.sh $1 $size 200
  size=$(($size+$4))
done
