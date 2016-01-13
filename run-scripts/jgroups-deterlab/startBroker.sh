#!/bin/bash
# $1 contains the server's id and $2, $3, $4 contain the 3 server ips.

#Load settings
SCRIPT_DIR=$( cd $( dirname "${BASH_SOURCE[0]}" ) && pwd )
. $SCRIPT_DIR/settings.sh

RUN_DIR=/opt/broker
MAIN_DIR=/proj/midonet/lattest

#install midonet-tools for mn-conf
sudo dpkg -i $MIDONET_TOOLS_PKG

sudo touch /etc/midonet_host_id.properties
sudo chmod 777 /etc/midonet_host_id.properties

#Set open file limit
ulimit -n 500000

mkdir -p $RUN_DIR
cp $BROKER_INSTALL_SRC/* $RUN_DIR

if [ "$1" = "1" ]; then
  echo "zookeeper.zookeeper_hosts:\"zoo1:2181,zoo2:2181,zoo3:2181\"" | MIDO_ZOOKEEPER_HOSTS="zoo1:2181,zoo2:2181,zoo3:2181" mn-conf set -t default  
	echo -e "Creating and awaiting mn-conf init barrier...\n"
  /usr/testbed/bin/emulab-sync -i 2 -n mnconfbarrier
else
	echo -e "awaiting mn-conf init barrier...\n"
  /usr/testbed/bin/emulab-sync -n mnconfbarrier
fi

echo "\nSTARTING BROKER \n"
java -Djava.net.preferIPv4Stack=true -Dzookeeper.zookeeper_hosts=zoo1:2181,zoo2:2181,zoo3:2181 -Dcom.sun.management.jmxremote.port=9996 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -cp $RUN_DIR:$RUN_DIR/$CLUSTER_JAR_NAME org.midonet.cluster.data.storage.jgroups.JGroupsBroker 12345 clusternode$1 &
