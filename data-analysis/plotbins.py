#!/usr/bin/python

import matplotlib.pyplot as plt
import matplotlib
from numpy.random import normal
from os import listdir
from os.path import isfile, join, isdir
import numpy as np

EXPERIMENT_DIR = "scratch/readers-vs-latency/MMTB-1w40c100ups1000ts6000x"
PLOT_LATENCIES = False
PLOT_BOXPLOT = True
PLOT_HISTOGRAM = False

def processDataFile(filename):
    with open(filename) as f:
        content = f.readlines()
        content = content[1:]
        content = [int(x.strip('\n'))/1000.0 for x in content]

    return content


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

    for x in latencies[:-1]:
        plt.plot(x)

plt.show()