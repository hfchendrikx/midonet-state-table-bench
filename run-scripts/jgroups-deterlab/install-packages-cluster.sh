#!/bin/bash
#$1 use oracle jvm
#$2 install zookeeper
sudo apt-get update

#Load settings
SCRIPT_DIR=$( cd $( dirname "${BASH_SOURCE[0]}" ) && pwd )
. $SCRIPT_DIR/settings.sh

$SETUP_ROOT_DIR/install-java.sh $1

if [ $2 -eq 1 ]; then
  sudo apt-get -y install zookeeper
fi

sudo apt-get -y install nmon
