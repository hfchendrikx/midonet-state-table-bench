#!/usr/bin/python
from numpy import sqrt
import numpy
import matplotlib.pyplot as plt
import matplotlib
import re
from experimentreader import *
from numpy.random import normal
from os import listdir
from os.path import isfile, join, isdir
from matplotlib.ticker import MaxNLocator

EXPERIMENT_DIR = "final-raw-data/final-2000-10kups/exp"

#########################
#Plot readers vs latency#
#########################

#########################
# This part plots the graphs by calculating the statistics from the raw data
#########################
# readers = []
# latency = []
# deviation = []
# max95th = []
#
# for experiment in experiments:
#     grandSummary = calculateExperimentStatistics("scratch/readers-vs-latency/"+experiment)#calculateOverallExperimentSummary(readOldExperimentSummaries("scratch/readers-vs-latency/"+experiment))
#     readers.append(experiments[experiment]['readers'])
#     latency.append(grandSummary['mean'])
#     deviation.append(grandSummary['stddev'])
#     max95th.append(grandSummary['95thmax'])
#
# plt.title("Number of readers vs Latency")
# plt.xlabel("Number of readers")
# plt.ylabel("Latency [ms]")
#
# data = zip(readers, latency, deviation, max95th)
# x, y, err, max95th = zip(*sorted(data))
#
# plt.plot(x, y, linestyle='--', marker='o', label="Grand mean")
# plt.plot(x, max95th, linestyle='', marker='D', color='r', label="Max 95th percentile of one node")
# plt.errorbar(x, y, yerr=err, linestyle=' ', color='b', label="Pooled standard deviation")


def plot_maps_vs_latency(benchmark_directory, name, color='g', linestye='--'):
    #########################
    # This part plots the graphs by calculating the statistics from the summaries
    #########################
    experiments = listExperiments(benchmark_directory)
    print experiments
    maps = []
    latency = []
    deviation = []
    max95th = []
    max9999th = []
    for experiment in experiments:
        grandSummary = calculateOverallExperimentSummary(readOldExperimentSummaries(benchmark_directory + "/"+experiment))
        maps.append(experiments[experiment]['maps'])
        latency.append(grandSummary['mean'])
        deviation.append(grandSummary['stddev'])
        max95th.append(grandSummary['95thmax'])
        max9999th.append(grandSummary['9999thmax'])


    data = zip(maps, latency, deviation, max95th, max9999th)
    x, y, err, max95th, max9999th = zip(*sorted(data))

    print "Serie: " + name
    print "X:     " + str(x)
    print "99.99: " + str(max9999th)

    plt.plot(x, y, linestyle=linestye, marker='o', color=color, label=name + r' Average of all nodes')
    plt.plot(x, max95th, linestyle='', marker='D', color=color, label=name + r' Max $95^{th}$ percentile')
    plt.plot(x, max9999th, linestyle='', marker='x', color=color, label=name + r' Max $99.99^{th}$ percentile')
    plt.errorbar(x, y, yerr=err, linestyle=' ', color=color)

fig = plt.figure()

plt.subplot(2, 1, 1);
plt.title("Number of maps vs Latency [SDD]")
#plt.xlabel("Number of maps")
plt.ylabel("Latency [ms]")
#Plot here
plot_maps_vs_latency("final-raw-data/final-250-5000-10k-20nodes/exp",  "10k ups", "blue")
plot_maps_vs_latency("final-raw-data/final-250-5000-20k-20nodes/exp", "20k ups", "orange")
#plot_maps_vs_latency("final-raw-data/final-250-5000-100k-20nodes/exp", "100k", "red")
#plt.legend(prop={'size':12}, loc="upper left")
plt.tick_params(
    axis='x',          # changes apply to the x-axis
    which='both',      # both major and minor ticks are affected
    bottom='on',      # ticks along the bottom edge are off
    top='on',         # ticks along the top edge are off
    labelbottom='off') # labels along the bottom edge are off
plt.ylim(0, 2500)
plt.xlim(0, 5100)


plt.subplot(2, 1, 2);
plt.title("Number of maps vs Latency [HDD]")
plt.xlabel("Number of maps")
plt.ylabel("Latency [ms]")
plot_maps_vs_latency("final-raw-data/final-250-2000-10k-20nodes-hdd/exp", "10k ups", "blue")
plot_maps_vs_latency("final-raw-data/final-250-2000-20k-20nodes-hdd/exp", "20k ups", "orange")
plt.legend(prop={'size':12}, loc="lower right")

#plt.ylim(0, 2500)
plt.xlim(0, 5100)

fig.tight_layout();
plt.show()
