#!/usr/bin/python

import matplotlib.pyplot as plt
import matplotlib
from numpy.random import normal
from os import listdir
from os.path import isfile, join, isdir
import numpy as np
import jmxlogreader
from experimentreader import  *
from jmxlogreader import *
from nmonlogreader import *

BASE_DIR = "jgroups-data/test"

PLOT_CPU_CORES = True
PLOT_NMON_MEMORY = True
OVERLAY_JMX_CPU = True
OVERLAY_JMX_MEMORY = True
OVERLAY_JMX_GC = False
OVERLAY_JMX_GC_TIME = False
OVERLAY_JMX_GC_TIME_JAVA_8 = False
OVERLAY_JMX_ZK_MAXLATENCY = True
OVERLAY_JMX_ZK_PACKETS = True
OVERLAY_NMON_NETWORK = True
OVERLAY_NMON_NETWORK_INTERFACES = ['eth2','eth4', 'eth5']
OVERLAY_NMON_DISK_RATE = True
OVERLAY_NMON_DISK_BUSY = True
OVERLAY_NMON_DISK_DISKS = ['sda']

JMXLOG_FILE_DIRECTORY = BASE_DIR + "/jmx";
NMONLOG_FILE_DIRECTORY = BASE_DIR + "/nmon";

if PLOT_CPU_CORES:
    data = readNmonDirectory(NMONLOG_FILE_DIRECTORY)

    for nodename in data:
        plotCpuCoreUsage(data[nodename])
        plt.suptitle(BASE_DIR + ": " + nodename)

if PLOT_NMON_MEMORY:
    data = readNmonDirectory(NMONLOG_FILE_DIRECTORY)
    plotNmonMemUsage(data)

if OVERLAY_JMX_CPU:
    plt.figure()
    plt.title("CPU " + BASE_DIR)
    plt.xlabel("Time since start of measurements [s]")
    data = readKeyLog(JMXLOG_FILE_DIRECTORY + "/" + getFilename(LOG_JVM_KAFKA, CLUSTER_NODE_2, LOG_TYPE_CPU))
    plotCpuUsage(data, "Kafka node 2")
    data = readKeyLog(JMXLOG_FILE_DIRECTORY + "/" + getFilename(LOG_JVM_KAFKA, CLUSTER_NODE_1, LOG_TYPE_CPU))
    plotCpuUsage(data, "Kafka node 1")
    data = readKeyLog(JMXLOG_FILE_DIRECTORY + "/" + getFilename(LOG_JVM_KAFKA, CLUSTER_NODE_3, LOG_TYPE_CPU))
    plotCpuUsage(data, "Kafka node 3")
    plt.ylim(0, 100)
    plt.ylabel("Load average [%]")
    plt.legend()

if OVERLAY_JMX_MEMORY:
    plt.figure()
    plt.title("Memory " + BASE_DIR)
    plt.xlabel("Time since start of measurements [s]")
    data = readKeyLog(JMXLOG_FILE_DIRECTORY + "/" + getFilename(LOG_JVM_KAFKA, CLUSTER_NODE_2, LOG_TYPE_MEMORY))
    plotHeapUsage(data, "Kafka node 2", used=True)
    data = readKeyLog(JMXLOG_FILE_DIRECTORY + "/" + getFilename(LOG_JVM_KAFKA, CLUSTER_NODE_1, LOG_TYPE_MEMORY))
    plotHeapUsage(data, "Kafka node 1", used=True)
    data = readKeyLog(JMXLOG_FILE_DIRECTORY + "/" + getFilename(LOG_JVM_KAFKA, CLUSTER_NODE_3, LOG_TYPE_MEMORY))
    plotHeapUsage(data, "Kafka node 3", used=True)
    plt.ylabel("Size [MB]")
    plt.legend()

if OVERLAY_JMX_ZK_MAXLATENCY:
    plt.figure()
    plt.title("Zookeeper Max Latency " + BASE_DIR)
    plt.xlabel("Time since start of measurements [s]")
    data = readKeyLog(JMXLOG_FILE_DIRECTORY + "/" + getFilename(LOG_JVM_ZOOKEEPER, CLUSTER_NODE_2, LOG_TYPE_GENERAL))
    plotZookeeperMaxLatency(data, "ZK node 2")
    data = readKeyLog(JMXLOG_FILE_DIRECTORY + "/" + getFilename(LOG_JVM_ZOOKEEPER, CLUSTER_NODE_1, LOG_TYPE_GENERAL))
    plotZookeeperMaxLatency(data, "ZK node 1")
    data = readKeyLog(JMXLOG_FILE_DIRECTORY + "/" + getFilename(LOG_JVM_ZOOKEEPER, CLUSTER_NODE_3, LOG_TYPE_GENERAL))
    plotZookeeperMaxLatency(data, "ZK node 3")
    plt.ylabel("Latency [ms]")
    plt.legend()

if OVERLAY_JMX_ZK_PACKETS:
    plt.figure()
    plt.title("Zookeeper Packets " + BASE_DIR)
    plt.xlabel("Time since start of measurements [s]")
    data = readKeyLog(JMXLOG_FILE_DIRECTORY + "/" + getFilename(LOG_JVM_ZOOKEEPER, CLUSTER_NODE_2, LOG_TYPE_GENERAL))
    plotZookeeperPacketsPerInterval(data, "ZK node 2", color="g")
    data = readKeyLog(JMXLOG_FILE_DIRECTORY + "/" + getFilename(LOG_JVM_ZOOKEEPER, CLUSTER_NODE_1, LOG_TYPE_GENERAL))
    plotZookeeperPacketsPerInterval(data, "ZK node 1", color="r")
    data = readKeyLog(JMXLOG_FILE_DIRECTORY + "/" + getFilename(LOG_JVM_ZOOKEEPER, CLUSTER_NODE_3, LOG_TYPE_GENERAL))
    plotZookeeperPacketsPerInterval(data, "ZK node 3", color="b")

    plt.ylabel("# of packets per interval")
    plt.gca().yaxis.set_major_locator(MaxNLocator(integer=True))
    plt.legend()

if OVERLAY_JMX_GC:
    plt.figure()
    plt.title("Garbage Collections " + BASE_DIR)
    plt.xlabel("Time since start of measurements [s]")
    data = readKeyLog(JMXLOG_FILE_DIRECTORY + "/" + getFilename(LOG_JVM_KAFKA, CLUSTER_NODE_2, LOG_TYPE_GARBAGE_COLLECTION), name_length=2)
    plotGcMoments(data, "Kafka node 2", color = 'g')
    data = readKeyLog(JMXLOG_FILE_DIRECTORY + "/" + getFilename(LOG_JVM_KAFKA, CLUSTER_NODE_1, LOG_TYPE_GARBAGE_COLLECTION), name_length=2)
    plotGcMoments(data, "Kafka node 1", color = 'r')
    data = readKeyLog(JMXLOG_FILE_DIRECTORY + "/" + getFilename(LOG_JVM_KAFKA, CLUSTER_NODE_3, LOG_TYPE_GARBAGE_COLLECTION), name_length=2)
    plotGcMoments(data, "Kafka node 3", color = 'b')
    plt.ylabel("Number of GC's performed during measurement interval")
    plt.gca().yaxis.set_major_locator(MaxNLocator(integer=True))
    plt.legend()

if OVERLAY_JMX_GC_TIME:
    plt.figure()
    plt.title("Garbage Collection Time " + BASE_DIR)
    plt.xlabel("Time since start of measurements [s]")
    data = readKeyLog(JMXLOG_FILE_DIRECTORY + "/" + getFilename(LOG_JVM_KAFKA, CLUSTER_NODE_2, LOG_TYPE_GARBAGE_COLLECTION), name_length=2)
    plotTimeSpentInGC(data, "Kafka node 2", color = 'g')
    data = readKeyLog(JMXLOG_FILE_DIRECTORY + "/" + getFilename(LOG_JVM_KAFKA, CLUSTER_NODE_1, LOG_TYPE_GARBAGE_COLLECTION), name_length=2)
    plotTimeSpentInGC(data, "Kafka node 1", color = 'r')
    data = readKeyLog(JMXLOG_FILE_DIRECTORY + "/" + getFilename(LOG_JVM_KAFKA, CLUSTER_NODE_3, LOG_TYPE_GARBAGE_COLLECTION), name_length=2)
    plotTimeSpentInGC(data, "Kafka node 3", color = 'b')
    plt.ylabel("Time spent in GC during interval")
    plt.gca().yaxis.set_major_locator(MaxNLocator(integer=True))
    plt.legend()

if OVERLAY_JMX_GC_TIME_JAVA_8:
    plt.figure()
    plt.title("Garbage Collection Time " + BASE_DIR)
    plt.xlabel("Time since start of measurements [s]")
    data = readKeyLog(JMXLOG_FILE_DIRECTORY + "/" + getFilename(LOG_JVM_KAFKA, CLUSTER_NODE_2, LOG_TYPE_GARBAGE_COLLECTION), name_length=2)
    plotTimeSpentInGC_Java8Default(data, "Kafka node 2", color = 'g')
    data = readKeyLog(JMXLOG_FILE_DIRECTORY + "/" + getFilename(LOG_JVM_KAFKA, CLUSTER_NODE_1, LOG_TYPE_GARBAGE_COLLECTION), name_length=2)
    plotTimeSpentInGC_Java8Default(data, "Kafka node 1", color = 'r')
    data = readKeyLog(JMXLOG_FILE_DIRECTORY + "/" + getFilename(LOG_JVM_KAFKA, CLUSTER_NODE_3, LOG_TYPE_GARBAGE_COLLECTION), name_length=2)
    plotTimeSpentInGC_Java8Default(data, "Kafka node 3", color = 'b')
    plt.ylabel("Time spent in GC during interval")
    plt.gca().yaxis.set_major_locator(MaxNLocator(integer=True))
    plt.legend()


if OVERLAY_NMON_NETWORK:
    plt.figure()
    plt.title("Network Usage " + BASE_DIR)
    plt.xlabel("Time since start of measurements [s]")
    data = readNmonDirectory(NMONLOG_FILE_DIRECTORY)

    for nodename in data:
        for interface in OVERLAY_NMON_NETWORK_INTERFACES:
            plotNetworkUsage(data[nodename], nodename, interface)

    plt.ylabel("Rate [KB/s]")
    plt.legend()

if OVERLAY_NMON_DISK_RATE:
    plt.figure()
    plt.title("Disk Read/Write " + BASE_DIR)
    plt.xlabel("Time since start of measurements [s]")
    data = readNmonDirectory(NMONLOG_FILE_DIRECTORY)

    for nodename in data:
        for disk in OVERLAY_NMON_DISK_DISKS:
            plotDiskUsage(data[nodename], nodename, disk)

    plt.ylabel("Rate [KB/s]")
    plt.legend()

if OVERLAY_NMON_DISK_BUSY:
    plt.figure()
    plt.title("Disk Busy " + BASE_DIR)
    plt.xlabel("Time since start of measurements [s]")
    data = readNmonDirectory(NMONLOG_FILE_DIRECTORY)

    for nodename in data:
        for disk in OVERLAY_NMON_DISK_DISKS:
            plotDiskBusy(data[nodename], nodename, disk)

    plt.ylabel("Busy [%]")
    plt.legend()


#ax = plt.gca()  # get the current axes
#ax.relim()      # make sure all the data fits
#ax.autoscale()  # auto-scale

plt.show()
