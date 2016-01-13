#!/bin/bash
#This is executed on the clusternods by the restart-cluster.sh script
echo "Running on $(hostname)"
echo "Clearing zookeeper"
sudo rm -Rf /solidstatedrive/*
sudo rm -Rf /var/lib/zookeeper/version-2

sudo mkdir /solidstatedrive/zookeeper
sudo chmod 777 /solidstatedrive/zookeeper
