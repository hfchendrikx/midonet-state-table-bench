#!/bin/bash
# $1 contains the server's id and $2, $3, $4 contain the 3 server ips.

ZK_DIR=/usr/share/zookeeper
LOG_DIR=/var/lib/zookeeper/version-2
CONFIG_DIR=/etc/zookeeper/conf

PARAMS=("$@")
ZOOKEEPER_NODE_ID=$1
ZOOKEEPER_NODES=(${PARAMS[@]:1})

echo "ID Of this zookeeper server is $ZOOKEEPER_NODE_ID"
echo "Zookeeper servers configured: ${ZOOKEEPER_NODES[@]}"

# stop zookeeper
#sudo $ZK_DIR/bin/zkServer.sh stop

# clear zookeeper logs
#sudo rm -rf $LOG_DIR
#sudo mkdir $LOG_DIR
#sudo rm -rf $CONFIG_DIR/myid

# edit the zookeeper config
for ((i=0; i<${#ZOOKEEPER_NODES[@]}; i++));
do
  echo "Server $i ${ZOOKEEPER_NODES[i]}"
  sudo sed -i '$a'" server.$((i+1))=${ZOOKEEPER_NODES[i]}:2888:3888" $CONFIG_DIR/zoo.cfg
done

#sudo sed -i '$a dataLogDir=/solidstatedrive/zookeeper' $CONFIG_DIR/zoo.cfg
sudo touch $CONFIG_DIR/myid
sudo chown zookeeper:zookeeper $CONFIG_DIR/myid
echo $1 | sudo tee $CONFIG_DIR/myid

# start zookeeper
sudo JVMFLAGS="-Xmx2048m -Dcom.sun.management.jmxremote.port=9998 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false" $ZK_DIR/bin/zkServer.sh start
