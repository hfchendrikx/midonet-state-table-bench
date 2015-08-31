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

EXPERIMENT_DIR = "scratch/MMTB-1w19c200ups1000ts60000x"
JMXLOG_FILE_DIRECTORY = EXPERIMENT_DIR + "/logs";
OVERLAY_JMX = True
PLOT_LATENCIES = True
PLOT_BOXPLOT = False
PLOT_HISTOGRAM = False

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

    plt.boxplot(latencies)
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
    plt.xlabel("Time")
    plt.ylabel("Latency [ms]")

    timeSeriesLatencies = calculateTimeSeriesLatencies(EXPERIMENT_DIR)

    for x in timeSeriesLatencies:
        if not x == 'start':
            plt.plot(timeSeriesLatencies[x][0], timeSeriesLatencies[x][1])

    start = long(timeSeriesLatencies['start'])

    print start

    if OVERLAY_JMX:
        plt.twinx()

        data = readKeyLog(JMXLOG_FILE_DIRECTORY + "/" + getFilename(LOG_JVM_KAFKA, CLUSTER_NODE_2, LOG_TYPE_MEMORY))
        plotHeapUsage(data, "Kafka node 2", used=True, x0=start)
        data = readKeyLog(JMXLOG_FILE_DIRECTORY + "/" + getFilename(LOG_JVM_KAFKA, CLUSTER_NODE_1, LOG_TYPE_MEMORY))
        plotHeapUsage(data, "Kafka node 1", used=True, x0=start)
        data = readKeyLog(JMXLOG_FILE_DIRECTORY + "/" + getFilename(LOG_JVM_KAFKA, CLUSTER_NODE_3, LOG_TYPE_MEMORY))
        plotHeapUsage(data, "Kafka node 3", used=True, x0=start)

plt.show()