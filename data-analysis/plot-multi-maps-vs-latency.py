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

EXPERIMENT_DIR = "scratch/100-1000maps-NOT-CLEARED/exp"

#########################
#Plot readers vs latency#
#########################
experiments = listExperiments(EXPERIMENT_DIR)

print experiments

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


#########################
# This part plots the graphs by calculating the statistics from the summaries
#########################
maps = []
latency = []
deviation = []
max95th = []
max9999th = []
for experiment in experiments:
    grandSummary = calculateOverallExperimentSummary(readOldExperimentSummaries(EXPERIMENT_DIR + "/"+experiment))
    maps.append(experiments[experiment]['maps'])
    latency.append(grandSummary['mean'])
    deviation.append(grandSummary['stddev'])
    max95th.append(grandSummary['95thmax'])
    #max9999th.append(grandSummary['9999thmax'])

plt.title("Maps vs Latency")
plt.xlabel("Number of maps")
plt.ylabel("Latency [ms]")

data = zip(maps, latency, deviation, max95th)
x, y, err, max95th = zip(*sorted(data))

plt.plot(x, y, linestyle='--', marker='o', color='g', label="Grand mean")
plt.plot(x, max95th, linestyle='', marker='D', color='g', label="Max 95th percentile of one node")
#plt.plot(x, max9999th, linestyle='', marker='D', color='g', label="Max 99.99th percentile of one node")
plt.errorbar(x, y, yerr=err, linestyle=' ', color='g', label="Pooled standard deviation")

plt.legend(prop={'size':12})
plt.ylim(0, 1000)
plt.xlim(0, 1100)
plt.show()

