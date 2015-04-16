# Runs a benchmark ($1) for the given data structure (stored in $2) and for sizes comprised between
# $3 and $4 in increments of $5.
size=$3

while [ $size -le "$4" ]
do
  ./runMapSetBench.sh $1 $2 $size 200
  size=$(($size+$5))
done
