set ns [new Simulator]
source tb_compat.tcl

proc cluster_startup_command {start_zookeeper start_kafka zookeeper_id kafka_id init_ssd zookeeper_nodes  logfile} {
  # $1 contains the number of testbench nodes
  # $2 contains the number of cluster nodes
  # $3 use oracle jvm
  # $4 start zookeeper on this node
  # $5 start kafka on this node
  # $6 Zookeeper ID of this node
  # $7 Kafka ID of this node
  # $8 Init ssd
  # $9...n (rest are the addresses of the zookeeper nodes)
  global nrbenchnodes
  global nrclusternodes
  global use_oracle_jdk
  return "/proj/midonet/lattest/startup-cluster.sh $nrbenchnodes $nrclusternodes $use_oracle_jdk $start_zookeeper $start_kafka $zookeeper_id $kafka_id $init_ssd $zookeeper_nodes >& /proj/midonet/lattest/startlog/$logfile.log"
}

###
###REMEMBER TO CHANGE THE SLURM CONFIGURATION IF YOU CHANGE THE NUMBER OF BENCH NODES
###
set use_oracle_jdk 1
set nrbenchnodes 2
set nrclusternodes 6 #Zookeeper + kafka nodes
set seperate_zookeeper_nodes 1

# Cluster nodes
set clusternode1 [$ns node]
tb-set-node-os $clusternode1 Ubuntu1404-64-STD
set clusternode2 [$ns node]
tb-set-node-os $clusternode2 Ubuntu1404-64-STD
set clusternode3 [$ns node]
tb-set-node-os $clusternode3 Ubuntu1404-64-STD

if {$seperate_zookeeper_nodes} {
  set zoo1 [$ns node]
  tb-set-node-os $zoo1 Ubuntu1404-64-STD
  set zoo2 [$ns node]
  tb-set-node-os $zoo2 Ubuntu1404-64-STD
  set zoo3 [$ns node]
  tb-set-node-os $zoo3 Ubuntu1404-64-STD
}

# Better hardware for cluster nodes
tb-set-hardware $clusternode1 dl380g3
tb-set-hardware $clusternode2 dl380g3
tb-set-hardware $clusternode3 dl380g3

#JMX Monitor node
set jmxmonitor [$ns node]
tb-set-node-os $jmxmonitor Ub1404x64-slurmMpi
tb-set-node-tarfiles $jmxmonitor /usr/share/jmxtrans /proj/midonet/tarfiles/jmxtrans-251-dist.tar.gz
tb-set-node-startcmd $jmxmonitor "/proj/midonet/lattest/jmx/auto-start-jmx.sh $seperate_zookeeper_nodes"

#Put all nodes on one LAN
set lanstr "$clusternode1 $clusternode2 $clusternode3 $jmxmonitor "
if {$seperate_zookeeper_nodes} {
  append lanstr "$zoo1 $zoo2 $zoo3 "
}
tb-set-sync-server $clusternode1

###
# Clusternodes running zookeepr and kafka
###
set cluster_tb [list]
set zoo_tb [list]

#Put kafka tars on the cluster nodes
lappend cluster_tb /usr/share/kafka /proj/midonet/tarfiles/kafka_2.10-0.8.2.1.tgz

#Put java tars on all cluster nodes
if {$use_oracle_jdk} {
  lappend cluster_tb /opt/java /proj/midonet/tarfiles/jdk-7u79-linux-x64.tar.gz
  if {$seperate_zookeeper_nodes} {
    tb-set-node-tarfiles $zoo1 /opt/java /proj/midonet/tarfiles/jdk-7u79-linux-x64.tar.gz
    tb-set-node-tarfiles $zoo2 /opt/java /proj/midonet/tarfiles/jdk-7u79-linux-x64.tar.gz
    tb-set-node-tarfiles $zoo3 /opt/java /proj/midonet/tarfiles/jdk-7u79-linux-x64.tar.gz
  }
}

eval tb-set-node-tarfiles $clusternode1 $cluster_tb
eval tb-set-node-tarfiles $clusternode2 $cluster_tb
eval tb-set-node-tarfiles $clusternode3 $cluster_tb



if {$seperate_zookeeper_nodes} {
  tb-set-node-startcmd $clusternode1 [cluster_startup_command 0 1 0 1 1 "zoo1 zoo2 zoo3" "clusternode1"]
  tb-set-node-startcmd $clusternode2 [cluster_startup_command 0 1 0 2 1 "zoo1 zoo2 zoo3" "clusternode2"]
  tb-set-node-startcmd $clusternode3 [cluster_startup_command 0 1 0 3 1 "zoo1 zoo2 zoo3" "clusternode3"]
  tb-set-node-startcmd $zoo1 [cluster_startup_command 1 0 1 0 0 "zoo1 zoo2 zoo3" "zoo1"]
  tb-set-node-startcmd $zoo2 [cluster_startup_command 1 0 2 0 0 "zoo1 zoo2 zoo3" "zoo2"]
  tb-set-node-startcmd $zoo3 [cluster_startup_command 1 0 3 0 0 "zoo1 zoo2 zoo3" "zoo3"]
} else {
  tb-set-node-startcmd $clusternode1 [cluster_startup_command 1 1 1 1 1 "clusternode1 clusternode2 clusternode3" "clusternode1"]
  tb-set-node-startcmd $clusternode2 [cluster_startup_command 1 1 2 2 1 "clusternode1 clusternode2 clusternode3" "clusternode2"]
  tb-set-node-startcmd $clusternode3 [cluster_startup_command 1 1 3 3 1 "clusternode1 clusternode2 clusternode3" "clusternode3"]
}


###
# Testbench nodes
#
# REMEMBER TO CHANGE THE SLURM CONFIGURATION IF YOU CHANGE THE HARDWARE TYPE OF THE NODES
###
for {set i 1} {$i <= $nrbenchnodes} {incr i} {
  set benchnode($i) [$ns node]
  tb-set-node-os $benchnode($i) Ub1404x64-slurmMpi
  tb-set-hardware $benchnode($i) dl380g3
  append lanstr "$benchnode($i) "
  tb-set-node-startcmd $benchnode($i) "/proj/midonet/lattest/startup-bench.sh /proj/midonet/lattest/results/$i >& /proj/midonet/lattest/startlog/benchnode$i.log"
}

# Lans
set lan0 [$ns make-lan "$lanstr" 100000.0kb 0.0ms]

$ns rtproto Static
$ns run
