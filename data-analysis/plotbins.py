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

BASE_DIR = "scratch/oracle-seperated_cluster-500maps500ups"

PLOT_LATENCIES = True
OVERLAY_JMX_CPU = False
OVERLAY_JMX_MEMORY = False
OVERLAY_JMX_GC = False
OVERLAY_JMX_GC_TIME = False
OVERLAY_JMX_ZK_MAXLATENCY = False
OVERLAY_JMX_ZK_PACKETS = False
OVERLAY_NMON_NETWORK = True
OVERLAY_NMON_NETWORK_INTERFACES = ['eth0']
OVERLAY_NMON_DISK_RATE = False
OVERLAY_NMON_DISK_BUSY = False
OVERLAY_NMON_DISK_DISKS = ['sdb']

PLOT_BOXPLOT = False
PLOT_HISTOGRAM = False

EXPERIMENT_DIR = BASE_DIR + "/exp/MultiMMTB-10mpw10mpr500maps500ups1000ts150000x";
JMXLOG_FILE_DIRECTORY = BASE_DIR + "/jmx";
NMONLOG_FILE_DIRECTORY = BASE_DIR + "/nmon";

if PLOT_HISTOGRAM:
    for f in listdir(EXPERIMENT_DIR):
        if (isdir(EXPERIMENT_DIR + "/" + f)):
            filename = EXPERIMENT_DIR + "/" + f + "/raw-latency-data"
            if isfile(filename):
                test = processDataFile(filename)
                plt.hist(test, histtype="stepfilled",
                             bins=250, alpha=0.5, normed=True)


    plt.title("Histogram of all nodes")
    plt.xlabel("Latency [ms]")
    plt.ylabel("Frequency")

if PLOT_BOXPLOT:
    figure = plt.figure()

    latencies = list()
    all_latencies = list()
    plot_labels = list()

    for f in listdir(EXPERIMENT_DIR):
        if (isdir(EXPERIMENT_DIR + "/" + f)):
            filename = EXPERIMENT_DIR + "/" + f + "/raw-latency-data"
            if isfile(filename):
                plot_labels.append(f)
                data = processDataFile(filename)
                latencies.append(data)
                all_latencies = all_latencies + data

    plot_labels.append("ALL NODES")
    latencies.append(all_latencies)

    #plt.boxplot(latencies)
    plt.xticks(range(1,len(plot_labels)+1), plot_labels, rotation='vertical')

    means = [np.mean(x) for x in latencies]
    stddevs = [np.std(x) for x in latencies]
    per95 = [np.percentile(x, 95) for x in latencies]
    per99 = [np.percentile(x, 99) for x in latencies]
    plt.scatter(range(1,len(plot_labels)+1), means, color="g", label="Mean")
    plt.scatter(range(1,len(plot_labels)+1), stddevs, color="Y", marker="D", label="Standard deviation")
    plt.scatter(range(1,len(plot_labels)+1), per95, color="r", marker="x", label="95th percentile")
    plt.scatter(range(1,len(plot_labels)+1), per99, color="r", marker="x", label="99th percentile")
    plt.legend()

    plt.title("Latency on all nodes")
    plt.ylabel("Latency [ms]")

if PLOT_LATENCIES:
    plt.figure()
    plt.title("Latency over time")
    plt.xlabel("Time since start of test [s]")
    plt.ylabel("Latency [ms]")

    timeSeriesLatencies = calculateTimeSeriesLatencies(EXPERIMENT_DIR)

    for x in timeSeriesLatencies:
        if not x == 'start':
            pass#plt.plot(timeSeriesLatencies[x][0], timeSeriesLatencies[x][1])

    plt.plot(timeSeriesLatencies[timeSeriesLatencies.keys()[0]][0], timeSeriesLatencies[timeSeriesLatencies.keys()[0]][1])

    start = long(timeSeriesLatencies['start'])
    #plt.ylim(0, 1000)

    if OVERLAY_JMX_CPU:
        plt.twinx()
        data = readKeyLog(JMXLOG_FILE_DIRECTORY + "/" + getFilename(LOG_JVM_KAFKA, CLUSTER_NODE_2, LOG_TYPE_CPU))
        plotCpuUsage(data, "Kafka node 2", x0=start)
        data = readKeyLog(JMXLOG_FILE_DIRECTORY + "/" + getFilename(LOG_JVM_KAFKA, CLUSTER_NODE_1, LOG_TYPE_CPU))
        plotCpuUsage(data, "Kafka node 1", x0=start)
        data = readKeyLog(JMXLOG_FILE_DIRECTORY + "/" + getFilename(LOG_JVM_KAFKA, CLUSTER_NODE_3, LOG_TYPE_CPU))
        plotCpuUsage(data, "Kafka node 3", x0=start)
        plt.ylim(0, 50)
        plt.ylabel("Load average [%]")

    if OVERLAY_JMX_MEMORY:
        plt.twinx()
        data = readKeyLog(JMXLOG_FILE_DIRECTORY + "/" + getFilename(LOG_JVM_KAFKA, CLUSTER_NODE_2, LOG_TYPE_MEMORY))
        plotHeapUsage(data, "Kafka node 2", x0=start, used=True)
        data = readKeyLog(JMXLOG_FILE_DIRECTORY + "/" + getFilename(LOG_JVM_KAFKA, CLUSTER_NODE_1, LOG_TYPE_MEMORY))
        plotHeapUsage(data, "Kafka node 1", x0=start, used=True)
        data = readKeyLog(JMXLOG_FILE_DIRECTORY + "/" + getFilename(LOG_JVM_KAFKA, CLUSTER_NODE_3, LOG_TYPE_MEMORY))
        plotHeapUsage(data, "Kafka node 3", x0=start, used=True)
        plt.ylabel("Size [MB]")

    if OVERLAY_JMX_ZK_MAXLATENCY:
        #plt.twinx()
        data = readKeyLog(JMXLOG_FILE_DIRECTORY + "/" + getFilename(LOG_JVM_ZOOKEEPER, CLUSTER_NODE_2, LOG_TYPE_GENERAL))
        plotZookeeperMaxLatency(data, "ZK node 2", x0=start)
        data = readKeyLog(JMXLOG_FILE_DIRECTORY + "/" + getFilename(LOG_JVM_ZOOKEEPER, CLUSTER_NODE_1, LOG_TYPE_GENERAL))
        plotZookeeperMaxLatency(data, "ZK node 1", x0=start)
        data = readKeyLog(JMXLOG_FILE_DIRECTORY + "/" + getFilename(LOG_JVM_ZOOKEEPER, CLUSTER_NODE_3, LOG_TYPE_GENERAL))
        plotZookeeperMaxLatency(data, "ZK node 3", x0=start)
        #plt.ylabel("Latency [ms]")

    if OVERLAY_JMX_ZK_PACKETS:
        plt.twinx()
        data = readKeyLog(JMXLOG_FILE_DIRECTORY + "/" + getFilename(LOG_JVM_ZOOKEEPER, CLUSTER_NODE_2, LOG_TYPE_GENERAL))
        plotZookeeperPacketsPerInterval(data, "ZK node 2", color="g", x0=start)
        data = readKeyLog(JMXLOG_FILE_DIRECTORY + "/" + getFilename(LOG_JVM_ZOOKEEPER, CLUSTER_NODE_1, LOG_TYPE_GENERAL))
        plotZookeeperPacketsPerInterval(data, "ZK node 1", color="r", x0=start)
        data = readKeyLog(JMXLOG_FILE_DIRECTORY + "/" + getFilename(LOG_JVM_ZOOKEEPER, CLUSTER_NODE_3, LOG_TYPE_GENERAL))
        plotZookeeperPacketsPerInterval(data, "ZK node 3", color="b", x0=start)

        plt.ylabel("# of packets per interval")
        plt.gca().yaxis.set_major_locator(MaxNLocator(integer=True))

    if OVERLAY_JMX_GC:

        plt.twinx()

        data = readKeyLog(JMXLOG_FILE_DIRECTORY + "/" + getFilename(LOG_JVM_KAFKA, CLUSTER_NODE_2, LOG_TYPE_GARBAGE_COLLECTION), name_length=2)
        plotGcMoments(data, "Kafka node 2", color = 'g', x0=start)
        data = readKeyLog(JMXLOG_FILE_DIRECTORY + "/" + getFilename(LOG_JVM_KAFKA, CLUSTER_NODE_1, LOG_TYPE_GARBAGE_COLLECTION), name_length=2)
        plotGcMoments(data, "Kafka node 1", color = 'r', x0=start-100)
        data = readKeyLog(JMXLOG_FILE_DIRECTORY + "/" + getFilename(LOG_JVM_KAFKA, CLUSTER_NODE_3, LOG_TYPE_GARBAGE_COLLECTION), name_length=2)
        plotGcMoments(data, "Kafka node 3", color = 'b', x0=start+100)
        plt.ylabel("Number of GC's performed during measurement interval")
        plt.ylim(-2,30)
        plt.axhline(0)
        plt.gca().yaxis.set_major_locator(MaxNLocator(integer=True))

    if OVERLAY_JMX_GC_TIME:

        plt.twinx()

        data = readKeyLog(JMXLOG_FILE_DIRECTORY + "/" + getFilename(LOG_JVM_KAFKA, CLUSTER_NODE_2, LOG_TYPE_GARBAGE_COLLECTION), name_length=2)
        plotTimeSpentInGC(data, "Kafka node 2", color = 'g', x0=start)
        data = readKeyLog(JMXLOG_FILE_DIRECTORY + "/" + getFilename(LOG_JVM_KAFKA, CLUSTER_NODE_1, LOG_TYPE_GARBAGE_COLLECTION), name_length=2)
        plotTimeSpentInGC(data, "Kafka node 1", color = 'r', x0=start-100)
        data = readKeyLog(JMXLOG_FILE_DIRECTORY + "/" + getFilename(LOG_JVM_KAFKA, CLUSTER_NODE_3, LOG_TYPE_GARBAGE_COLLECTION), name_length=2)
        plotTimeSpentInGC(data, "Kafka node 3", color = 'b', x0=start+100)
        plt.ylabel("Time spent in GC during interval")
        plt.ylim(-2,400)
        plt.axhline(0)
        plt.gca().yaxis.set_major_locator(MaxNLocator(integer=True))


    if OVERLAY_NMON_NETWORK:
        plt.twinx()

        data = readNmonDirectory(NMONLOG_FILE_DIRECTORY)

        for nodename in data:
            for interface in OVERLAY_NMON_NETWORK_INTERFACES:
                plotNetworkUsage(data[nodename], nodename, interface, x0=start)

        plt.ylim(0, 100000)
        plt.ylabel("Rate [KB/s]")

    if OVERLAY_NMON_DISK_RATE:
        plt.twinx()

        data = readNmonDirectory(NMONLOG_FILE_DIRECTORY)

        for nodename in data:
            for disk in OVERLAY_NMON_DISK_DISKS:
                plotDiskUsage(data[nodename], nodename, disk, x0=start)

        plt.ylabel("Rate [KB/s]")

    if OVERLAY_NMON_DISK_BUSY:
        plt.twinx()

        data = readNmonDirectory(NMONLOG_FILE_DIRECTORY)

        for nodename in data:
            for disk in OVERLAY_NMON_DISK_DISKS:
                plotDiskBusy(data[nodename], nodename, disk, x0=start)

        plt.ylabel("Busy [%]")


    #ax = plt.gca()  # get the current axes
    #ax.relim()      # make sure all the data fits
    #ax.autoscale()  # auto-scale
    plt.xlim(-100, 700)
    plt.legend()

plt.show()