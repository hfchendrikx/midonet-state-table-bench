#!/usr/bin/python
from numpy import sqrt
import numpy
import matplotlib.pyplot as plt
import matplotlib
import re
from numpy.random import normal
from os import listdir
from os.path import isfile, join, isdir

MMTB_regexp = 'MMTB-(\d)w(\d+)c(\d+)ups(\d+)ts(\d+)x'

def processDataFile(filename):
    with open(filename) as f:
        content = f.readlines()
        content = content[1:]
        content = [int(x.strip('\n'))/1000.0 for x in content]

    return content


def processOldSummaryFile(filename):
    with open(filename) as f:
        content = f.readlines()
        content = content[1:]
        content = [float((x.split(' '))[-1]) for x in content]

    return {'mean': float(content[0]), 'stddev': float(content[1]), '95th': float(content[2]), 'file': filename}


def readOldExperimentSummaries(directory):
    summaries = {}

    for f in listdir(directory):
        if (isdir(directory + "/" + f)):
            filename = directory + "/" + f + "/summary"
            if (isfile(filename)):
                summaries[f] = processOldSummaryFile(filename)

    return summaries

def calculateOverallExperimentSummary(summaries):
    grandSummary = {'mean':0,'stddev':0,'95thmax': 0}
    meanAccumulator = 0.0
    nodeCount = 0
    pooledStdDev = 0.0

    for nodeName in summaries:
        meanAccumulator = meanAccumulator + summaries[nodeName]['mean']
        pooledStdDev = pooledStdDev + summaries[nodeName]['stddev']*summaries[nodeName]['stddev']

        if (grandSummary['95thmax'] < summaries[nodeName]['95th']):
            grandSummary['95thmax'] = summaries[nodeName]['95th']

        nodeCount = nodeCount + 1

    grandSummary['mean'] = meanAccumulator / nodeCount / 1000.0
    grandSummary['stddev'] = sqrt(pooledStdDev / nodeCount) / 1000.0
    grandSummary['95thmax'] = grandSummary['95thmax'] / 1000.0

    return grandSummary

def calculateExperimentStatistics(directory):
    latencies = []

    for f in listdir(directory):
        if (isdir(directory + "/" + f)):
            filename = directory + "/" + f + "/raw-latency-data"
            if (isfile(filename)):
                latencies = latencies + processDataFile(filename)

    return {'mean': numpy.mean(latencies), 'stddev': numpy.std(latencies), '95thmax':numpy.percentile(latencies, 95)}

def listExperiments(directory):
    experiments = {}

    for expirementTag in listdir(directory):
        if (isdir(directory + "/" + expirementTag)):
            parts = re.match(MMTB_regexp, expirementTag)
            if parts:
                experiments[expirementTag] = {
                    'writers': int(parts.group(1)),
                    'readers': int(parts.group(2)),
                    'write_rate' : int(parts.group(3))
                }

    return experiments



#########################
#Plot readers vs latency#
#########################
experiments = listExperiments("scratch/readers-vs-latency")

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
    grandSummary = calculateOverallExperimentSummary(readOldExperimentSummaries("scratch/readers-vs-latency/"+experiment))
    readers.append(experiments[experiment]['readers']+1)
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
plt.ylim(0, 20)
plt.xlim(0, 102)
plt.show()

