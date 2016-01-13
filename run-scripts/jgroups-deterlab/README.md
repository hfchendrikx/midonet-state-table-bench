# Running the JGroups based MergedMap benchmarks on Deterlab

## Introduction

This document explains how to run the MergedMap benchmarks in this repository on
Deterlab. For information about Deterlab visit the Deterlab website ( http://www.deterlab.net ).
Files names mentioned in this document without a location are most likely in the same directory
as this README.md file.

## 1. The benchmarks

The benchmarks are started by the main function of the classes described below. They can be configured
with the application.conf file. The application.example.conf contains an example configuration and
explains all the options.

#### MergedMapTestBench

This test bench runs a reader or writer for a single map on each jvm. This makes sure that there is no
overhead in switching threads, since there is one core for every reader or writer.

#### MultiMergedMapTestBench

This test bench runs multiple readers or writers on each jvm. This makes it possible to run benchmarks
with a large amount of maps without needing an insane amount of hardware.

#### BatchTestBench

This test bench is used to run the other two test benchnes multiple times with different parameters
without human intervention. It can also invoke a script in between each run. It is recommended to use
this bench on Deterlab even for a single run, because it can restart zookeeper and the brokers.

## 2. The setup

The hardware setup consists of the following machines:

* clusternode[1-3]: These nodes run the brokers.
* zoo[1-3]: These nodes run zookeeper.
* benchnode-[1-X]: These are the nodes that run the test bench. The amount of nodes can be configured in the NS file (see 3.2.1.)
* jmxmonitor: This node periodicaly reads and logs jmx metrics from the cluster and zookeeper nodes.

## 3. Setting up the environment

In this section the setup process of Deterlab is explained. If you run the benchmarks on a different
Slurm+OpenMPI compatible cluster, you can skip this section.

### 3.1. Before you begin

Before you begin you should gather the following files and packages. 

* The Midonet V5 Tools Package for Debian (something like midonet-tools_5.1.deb)
* The Midonet V5 Cluster Package with the JGroups code for Debian
* The Benchmarks jar (can be found in benchmarks/build/libs after running the gradle build task)

The following packages are also needed, but will already be available in the midonet group on Deterlab

* The OpenJDK Java 8 package for Debian
* The jmxtrans tar

The Deterlab experiment depends on two node images, these are also available in the midonet group on Deterlab

* Ubuntu1404-64-FileLi: This is a standard 64 bit Ubuntu 14.04 image, with an increased maximum open file limit
* Ub1404x64-slurmMpi: This is a standard 64 bit Ubuntu 14.04 images, with Slurm and OpenMPI for Java installed


### 3.2. Setting up the Deterlab experiment

1.  First you have to configure the *jgroups-deterlab.ns* file. The configuration block
    is at the top of the file. The following configuration is recommended:
    
    *Replace GROUP_NAME with the name of your group on Deterlab (probably midonet),
    and replace EXPERIMENT_NAME with the name of your experiment. You can choose this yourself.*
    
    ````tcl
    ############ CONFIGURATION ############
    
    # Number of nodes on which the benchmarks code runs
    # CHANGE THE SLURM CONFIGURATION ACCORDINGLY IF YOU CHANGE THE NUMBER OF BENCH NODES
    set nrbenchnodes 20
    
    # Where is the directory with the startup/init scripts, the directory where you found this README.md file?
    set setup_directory_location "/proj/GROUP_NAME/exp/EXPERIMENT_NAME/setup"
    # Where do you want to store the start log of each node?
    set startlog_directory_location "/proj/GROUP_NAME/exp/EXPERIMENT_NAME/logs/start"
    # Where is the jmxtrans tar located?
    set jmxtrans_tar_location "/proj/GROUP_NAME/tarfiles/jmxtrans-251-dist.tar.gz"
    
    #######################################
    ````

2.  Next you go to "Begin an experiment". Select your project and group. Fill in
    the experiment name. Use the same name as in step 1 (EXPERIMENT_NAME). Then select
    the ns file you configured in step 1. Leave the other options untouched. Make sure
    "Swap In Immediately" is **unchecked**. (It might be a good idea to set linktest to level 1.
    This way you get notified when there is a problem with the network connecting the nodes.) 

3.  Click "Submit", the experiment directories will now be created (/proj/GROUP_NAME/exp/EXPERIMENT_NAME)

### 3.3. Configuring the bash init scripts

1.  Edit the slurm configuration file, slurm.conf. Only the last few lines of this file
    have to be changed. Change the `benchnode-[1-20]` to the number of bench nodes configured in
    the ns file. So if you set that to for example 10, change `benchnode-[1-20]` to `benchnode-[1-10]`.
    You also need to set `CPUs` to the amount of cores available per node.
    
    ````
    # COMPUTE NODES
    NodeName=benchnode-[1-20] CPUs=10 State=UNKNOWN
    PartitionName=testbench Nodes=benchnode-[1-20] Default=YES 
    ````

2.  You also have to change the range of host names for the bench nodes in the file util/pdsh_hosts

3.  Next configure the settings in settings.sh. Point the `SETUP_ROOT_DIR` to the same directory as `setup_directory_location`
    
    ````bash
    PROJECT_DIR=/proj/GROUP_NAME
    SETUP_ROOT_DIR=$PROJECT_DIR/exp/EXPERIMENT_NAME/setup
    
    #Broker install source directory
    BROKER_INSTALL_SRC=$SETUP_ROOT_DIR/broker
    CLUSTER_JAR_NAME=midonet-cluster-5.1-SNAPSHOT-all.jar
    
    #Location of slurm configuration file
    SLURM_CONFIG_LOCATION=$SETUP_ROOT_DIR/slurm.conf
    
    #Java 8 package
    JAVA_8_UBUNTU_PKG=$PROJECT_DIR/rpms/openjdk-8-jre-headless_8u45-b14-1_amd64.deb
    
    #Midonet tools package
    MIDONET_TOOLS_PKG=$PROJECT_DIR/rpms/midonet-tools_5.1~201512041431.9d40059_all.deb
    ````

4.  Now copy the (modified) contents of the jgroups-deterlab directory into the `SETUP_ROOT_DIR` on Deterlab.
    Make sure you set the right permissions for the directory and make sure the scripts are executable.
    
5.  Put the midonet-cluster jar in the right place (`BROKER_INSTALL_SRC`/`CLUSTER_JAR_NAME`). Also make sure
    the Java 8, midonet-tools and jmxtrans packages are in the right place.
    
6.  Create the start log directory `startlog_directory_location`

### 3.4. Swapping in the experiment

1.  Now it is time to swap in the experiment. This can be done on your experiment page with the menu on the left.
    You will get hardware nodes assigned to you experiment. They will boot and start running the init scripts.
    The booting can take a long time, between 5 and 30 minutes, maybe even longer.
    
2.  Once all nodes are up and only once all nodes are up the init scripts will start. Before that you will not
    see anything in the start log directory. Now you should wait until all nodes complete there init scripts.
    Roughly speaking this is when a result_X.log file appeared for every bench node, where X is the ID of the node.

3.  At this point you can check if the environment is up and running by running the following commands on benchnode-1:
    
    
    ````bash
    hendrikx@benchnode-1:~$ sinfo
    PARTITION AVAIL  TIMELIMIT  NODES  STATE NODELIST
    testbenc*    up   infinite      2   idle benchnode-[1-2]
    ````
    
    You should see all your bench nodes in the idle state
    
    
    ````bash
    hendrikx@benchnode-1:~$ srun -n2 --ntasks-per-node=1 hostname
    benchnode-2.bench-test.midonet.isi.deterlab.net
    benchnode-1.bench-test.midonet.isi.deterlab.net
    ````
    
    Replace the 2 in -n2 by the number of bench nodes. You should see the hostname of every bench node.
    
## 4. Running the benchmarks

Now that the environment is up and running you can prepare the benchmarks and run them.

### 4.1. Configuring the benchmarks

1.  First you need to place the benchmarks jar in the benchmarks directory.

2.  Create an application.conf in the benchmarks directory. There is an example in that directory already.
    It is best to base your file on the example. Especially the following part is important.
    
    ````
    preRunScript = "/proj/GROUP_NAME/exp/EXPERIMENT_NAME/setup/prerun-scripts/restart-cluster.sh"
    preRunOutputFile = "/proj/GROUP_NAME/exp/EXPERIMENT_NAME/setup/benchmarks/prerun-script.out"
    
    Bookkeeper {
    basePath = "/proj/GROUP_NAME/exp/EXPERIMENT_NAME/setup/benchmarks/output"
    }
    ````
    
    If you keep these paths and replace *GROUP_NAME* and *EXPERIMENT_NAME* then you can be sure
    that the benchmark can find the prerun scripts and that the utility scripts know where to find
    the results of the benchmarks.
    
3.  If necessary, logging configuration for the brokers can changed in log4j2.xml and logback.xml. (or
    if you changed settings.sh to use different config files then you should change those). Logging for
    the benchmark nodes can be configured in the benchmarks/log4j.properties file.
    
4.  The last thing before running the benchmarks is to configure the amount of nodes
    to use in benchmarks/runbatch.sh. The block at the top of the file is the slurm batch job
    configuration. You should edit the line `#SBATCH --nodes=20` to match the number of bench nodes.
    
    ````
    #SBATCH --job-name=Statetable-benchmarks
    #SBATCH --output=test.out
    #SBATCH --error=test.err
    #SBATCH --partition=testbench
    #SBATCH --nodes=20
    ````
    
### 4.2. Starting the benchmark

Now it is time to start the benchmark. This can be done from the bench nodes. So first start an ssh session
with benchnode-1. Then go to then benchmarks directory and execute the following command.

````bash
hendrikx@benchnode-1:/proj/midonet/exp/test/setup/benchmarks$ sbatch runbatch.sh
Submitted batch job X
````

Now the benchmark should be running. The progress can be checked by monitoring the following files:

* benchmarks/test.out: This is the STDOUT of all benchmark java processes
* benchmarks/test.err: This is the STDERR of all benchmark java processes and status output of slurm
    (this file will contain some errors which say the prerun scripts can't access the solidstatedrive.
    you can ignore this.)
* benchmarks/prerun-script.out: Output of the prerun scripts for restarting the cluster in between runs
* broker-stdout/*: Here are the STDOUT files of all brokers
* benchmarks/output: This will container the benchmark results for each run (if this is the configured directory)

Running jobs can be cancelled with `scancel X`, where X is the number of the job.
The commands `squeue` and `sinfo` can be used to monitor slurm. (they can only be run from bench nodes)

### 4.3. Gathering and packaging results

Assuming all recommended directories are used, you can use the benchmarks/pkg-experiment to package the benchmarks results
together with the jmx and nmon measurements. (If you use different directories then you can modify the script)

````bash
hendrikx@benchnode-1:/proj/midonet/exp/batchtest2/setup/benchmarks$ ./pkg-experiment test
````

This will create an archive with the name test.tgz, this file can be downloaded from the test setup and visualized.
If you want to start another benchmark it is best to first clear the logs and old results, this can be done with
the clear-experiment script.

````bash
hendrikx@benchnode-1:/proj/midonet/exp/batchtest2/setup/benchmarks$ ./clear-experiment
pdsh@benchnode-1: jmxmonitor: ssh exited with exit code 255
````

You can ignore the error.

### 4.4. Terminating the experiment

When you are done running the experiments. You should terminate your experiment on Deterlab. This will remove everything
so make sure that you **download the results before**.

If you are not completely done yet, but are not using the setup for a while you should swap out your experiment, so that
other people can use the hardware reserved by you.

## Visualizing results

When the results are packaged using the pkg-experiment script they are organised in the following format:

````
+-- exp
|   +-- Experiment run 1
|       +-- 192.168.x.x-x
|       +-- 192.168.x.x-y
|   +-- Experiment run ...
+-- nmon
|   +-- nmon file clusternode1
|   +-- nmon file clusternode2
|   +-- nmon file clusternode3
+-- jmx
|   +-- jmx data file 1
|   +-- jmx data file 2
|   +-- jmx data file n
````

Results that are organised like this can be visualised using the python scripts in the data-analysis directory.

