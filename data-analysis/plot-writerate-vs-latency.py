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

def plot_writerate_vs_latency(benchmark_directory, name, color='g', linestye='--'):
    #########################
    # This part plots the graphs by calculating the statistics from the summaries
    #########################
    experiments = listExperiments(benchmark_directory)
    print experiments
    rate = []
    latency = []
    deviation = []
    max95th = []
    max9999th = []
    for experiment in experiments:
        grandSummary = calculateOverallExperimentSummary(readOldExperimentSummaries(benchmark_directory + "/"+experiment))
        rate.append(experiments[experiment]['updates_per_second'] * experiments[experiment]['maps'])
        latency.append(grandSummary['mean'])
        deviation.append(grandSummary['stddev'])
        max95th.append(grandSummary['95thmax'])
        max9999th.append(grandSummary['9999thmax'])


    data = zip(rate, latency, deviation, max95th, max9999th)
    x, y, err, max95th, max9999th = zip(*sorted(data))

    print "Serie: " + name
    print "X:     " + str(x)
    print "99.99: " + str(max9999th)

    plt.plot(x, y, linestyle=linestye, marker='o', color=color, label=name + r'Average of all nodes')
    plt.plot(x, max95th, linestyle='', marker='D', color=color, label=name + r'Max $95^{th}$ percentile')
    plt.plot(x, max9999th, linestyle='', marker='x', color=color, label=name + "Max $99.99^{th}$ percentile")
    plt.errorbar(x, y, yerr=err, linestyle=' ', color=color)

fig = plt.figure()

#plt.subplot(2, 1, 1);
#plt.title("Update rate vs Latency [SDD]")
plt.xlabel("Update rate [per second]")
plt.ylabel("Latency [ms]")
#Plot here
#plot_writerate_vs_latency("final-raw-data/writerate-2000maps-1k-100k-20nodes/exp",  "2000 maps", "blue")
#plot_writerate_vs_latency("final-raw-data/writerate-100k-300k-20nodes-2000maps-onlysummary/exp",  "2000 maps", "red")
plot_writerate_vs_latency("final-raw-data/writerate-transitionpoint/exp",  "", "green")
plot_writerate_vs_latency("final-raw-data/final-transition-point-10min/exp",  "", "orange")
plt.legend(prop={'size':12}, loc="lower right")

plt.ylim(0, 1100)
plt.xlim(9000, 41000)
fig.tight_layout();
plt.show()
