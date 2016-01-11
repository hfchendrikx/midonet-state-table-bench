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

EXPERIMENT_DIR = "jgroups-data/jgroups-netty-250-5000maps-20K/exp"

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

plt.title("Number of maps vs Latency")
#plt.xlabel("Number of maps")
plt.ylabel("Latency [ms]")
#Plot here
#plot_maps_vs_latency(EXPERIMENT_DIR,  "data", "blue")
#plot_maps_vs_latency("jgroups-data/jgroups-netty-250-5000maps-40K/exp", "40k", "red")
plot_maps_vs_latency("jgroups-data/jgroups-netty-250-5000maps-20K/exp", "20k", "orange")
plot_maps_vs_latency("jgroups-data/jgroups-netty-250-5000maps-10K/exp", "10k", "blue")
plot_maps_vs_latency("jgroups-data/5000-6666-8000maps/exp", "40k", "red")
plt.legend(prop={'size':11}, loc="upper left")
# plt.tick_params(
#     axis='x',          # changes apply to the x-axis
#     which='both',      # both major and minor ticks are affected
#     bottom='on',      # ticks along the bottom edge are off
#     top='on',         # ticks along the top edge are off
#     labelbottom='off') # labels along the bottom edge are off

#plt.ylim(0, 500)
plt.xlim(0, 8100)

fig.tight_layout();
plt.show()
