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

BASE_DIR = "scratch/crash-test-2maps-1k-interfacedown-opnieuw"

EXPERIMENT_DIR = BASE_DIR + "/exp/MMTB-2w9c1000ups1000ts180000x";
JMXLOG_FILE_DIRECTORY = BASE_DIR + "/jmx";
NMONLOG_FILE_DIRECTORY = BASE_DIR + "/nmon";

plt.figure()
#plt.title("Latency over time")
plt.xlabel("Time since start of test [s]")
plt.ylabel("Latency [ms]")

timeSeriesLatencies = loadTimeSeriesLatencies(EXPERIMENT_DIR)

firstKey = timeSeriesLatencies.keys()[0]
x = timeSeriesLatencies[firstKey][0]
y = timeSeriesLatencies[firstKey][1]

parts = []
currentX = [x[0]]
currentY = [y[0]]

for i in range(1 , len(x)):
    if (y[i] - y[i-1] > 1000):
        parts.append( (currentX, currentY) )
        currentX = []
        currentY = []

    currentX.append(x[i])
    currentY.append(y[i])

parts.append( (currentX, currentY) )

for part in parts:
    plt.plot(part[0], part[1], color='blue')

start = long(timeSeriesLatencies['start'])
#plt.ylim(0, 1000)

plt.yscale('log')
plt.xlim(0, 300)
plt.legend()

plt.show()