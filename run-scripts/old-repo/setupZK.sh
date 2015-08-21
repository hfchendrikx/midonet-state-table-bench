#!/bin/bash
# $1 contains the server's id and $2, $3, $4 contain the 3 server ips.

ZK_DIR=/usr/share/zookeeper
LOG_DIR=/var/lib/zookeeper/version-2
CONFIG_DIR=/etc/zookeeper/conf

# stop zookeeper
sudo $ZK_DIR/bin/zkServer.sh stop

# clear zookeeper logs
sudo rm -rf $LOG_DIR
sudo mkdir $LOG_DIR

# edit the zookeeper config
sudo sed -i '$a server.1='"$2":2888:3888 $CONFIG_DIR/zoo.cfg
sudo sed -i '$a server.2='"$3":2888:3888 $CONFIG_DIR/zoo.cfg
sudo sed -i '$a server.3='"$4":2888:3888 $CONFIG_DIR/zoo.cfg
sudo sed -i '$a'"$1" $CONFIG_DIR/myid

# start zookeeper
sudo $ZK_DIR/bin/zkServer.sh start
