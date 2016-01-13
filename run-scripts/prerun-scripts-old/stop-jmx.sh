#!/bin/bash
#This is executed on the clusternods by the restart-cluster.sh script
echo "Running on $(hostname)"
echo "Killing all java processes"
ps -e | grep java
sudo kill -9 $(pgrep java)
