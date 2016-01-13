#!/bin/bash
#This is executed on the clusternods by the restart-cluster.sh script
echo "Running on $(hostname)"
echo "Clearing kafka"
sudo rm -Rf /solidstatedrive/*
sudo rm -Rf /tmp/kafka-logs

sudo mkdir /solidstatedrive/kafka
sudo chmod 777 /solidstatedrive/kafka
