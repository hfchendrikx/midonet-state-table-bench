#!/bin/bash
#$1 use oracle jvm
#$2 install zookeeper
sudo apt-get update

./install-java.sh $1

if [ $2 -eq 1 ]; then
  sudo apt-get -y install zookeeper
fi

sudo apt-get -y install nmon
