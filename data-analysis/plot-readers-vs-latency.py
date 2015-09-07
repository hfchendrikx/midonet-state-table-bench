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

EXPERIMENT_DIR = "scratch/readers-vs-latency-10min-240/exp"

#########################
#Plot readers vs latency#
#########################
experiments = listExperiments(EXPERIMENT_DIR)

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
readers = []
latency = []
deviation = []
max95th = []
for experiment in experiments:
    grandSummary = calculateOverallExperimentSummary(readOldExperimentSummaries(EXPERIMENT_DIR + "/"+experiment))
    readers.append(experiments[experiment]['readers'])
    latency.append(grandSummary['mean'])
    deviation.append(grandSummary['stddev'])
    max95th.append(grandSummary['95thmax'])

plt.title("Number of readers vs Latency")
plt.xlabel("Number of readers")
plt.ylabel("Latency [ms]")

data = zip(readers, latency, deviation, max95th)
x, y, err, max95th = zip(*sorted(data))

plt.plot(x, y, linestyle='--', marker='o', color='g', label="Grand mean")
plt.plot(x, max95th, linestyle='', marker='D', color='g', label="Max 95th percentile of one node")
plt.errorbar(x, y, yerr=err, linestyle=' ', color='g', label="Pooled standard deviation")

plt.legend(prop={'size':12})
plt.ylim(0, 50)
plt.xlim(0, 242)
plt.show()

