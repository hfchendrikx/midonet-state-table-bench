
from os import listdir
from os.path import isfile, join, isdir
import re
from numpy import sqrt
import numpy

MMTB_regexp = '^MMTB-(\d)w(\d+)c(\d+)ups(\d+)ts(\d+)x$'
#MultiMMTB-5mpw5mpr1maps100ups1000ts30000x
MultiMMTB_regexp = '^MultiMMTB-(\d+)mpw(\d+)mpr(\d+)maps(\d+)ups(\d+)ts(\d+)x$'

def processDataFile(filename):
    try:
        with open(filename) as f:
            content = f.readlines()
            content = content[1:]
            content = [int(x.strip('\n'))/1000.0 for x in content]
    except ValueError:
        content = []

    return content

def processKeyFile(filename):
    data = {}
    with open(filename) as f:
        content = f.readlines()
        for line in content:
            parts = line.strip("\n").split("=")
            if len(parts) == 2:
                data[parts[0]] = parts[1]

    return data


def processOldSummaryFile(filename):
    with open(filename) as f:
        content = f.readlines()
        content = content[1:]
        content = [float((x.split('='))[-1]) for x in content]

    return {'mean': float(content[0]), 'stddev': float(content[1]), '95th': float(content[2]), '9999th': float(content[4]), 'file': filename}


def readOldExperimentSummaries(directory):
    summaries = {}

    for f in listdir(directory):
        if (isdir(directory + "/" + f)):
            filename = directory + "/" + f + "/summary"
            if (isfile(filename)):
                summaries[f] = processOldSummaryFile(filename)

    return summaries

def calculateOverallExperimentSummary(summaries):
    grandSummary = {'mean':0,'stddev':0,'95thmax': 0, '9999thmax': 0}
    meanAccumulator = 0.0
    nodeCount = 0
    pooledStdDev = 0.0

    for nodeName in summaries:
        meanAccumulator = meanAccumulator + summaries[nodeName]['mean']
        pooledStdDev = pooledStdDev + summaries[nodeName]['stddev']*summaries[nodeName]['stddev']

        if (grandSummary['95thmax'] < summaries[nodeName]['95th']):
            grandSummary['95thmax'] = summaries[nodeName]['95th']

        if (grandSummary['9999thmax'] < summaries[nodeName]['9999th']):
            grandSummary['9999thmax'] = summaries[nodeName]['9999th']

        nodeCount = nodeCount + 1

    grandSummary['mean'] = meanAccumulator / nodeCount / 1000.0
    grandSummary['stddev'] = sqrt(pooledStdDev / nodeCount) / 1000.0
    grandSummary['95thmax'] = grandSummary['95thmax'] / 1000.0
    grandSummary['9999thmax'] = grandSummary['9999thmax'] / 1000.0

    return grandSummary

def calculateExperimentStatistics(directory):
    latencies = []

    for f in listdir(directory):
        if (isdir(directory + "/" + f)):
            filename = directory + "/" + f + "/raw-latency-data"
            if (isfile(filename)):
                latencies = latencies + processDataFile(filename)

    return {'mean': numpy.mean(latencies), 'stddev': numpy.std(latencies), '95thmax':numpy.percentile(latencies, 95)}

def calculateTimeSeriesLatencies(directory):
    latencies = {}
    start = 0;
    for f in listdir(directory):
        if (isdir(directory + "/" + f)):
            filename = directory + "/" + f + "/raw-latency-data"
            if (isfile(filename)):
                data = processDataFile(filename)

                if (isfile(directory + "/" + f + "/timestamps")):
                    timestamps = processKeyFile(directory + "/" + f + "/timestamps")
                else:
                    continue


                start = float(timestamps['startbenchmark'])
                end = float(timestamps['endbenchmark'])
                delta = (end-start)/len(data);
                times = [delta*i / 1000 for i in range(0, len(data))]
                data = [float(y) for y in data]
                latencies[f] = (times, data)

    latencies['start'] = start

    return latencies

def loadTimeSeriesLatencies(directory):
    latencies = {}
    start = 0;
    for f in listdir(directory):
        if (isdir(directory + "/" + f)):
            filename = directory + "/" + f + "/raw-latency-data"
            if (isfile(filename)):
                timestamp_file = directory + "/" + f + "/raw-timestamp-data"

                if (not isfile(timestamp_file)):
                    print "Skipping " + f + " no raw-timestamp-data file found"
                    continue

                data = processDataFile(filename)
                timedata = processDataFile(timestamp_file)
                start = timedata[0];

                print "Node " + f + " data points: " + str(len(data))

                times = [y-start for y in timedata]
                data = [y for y in data]
                latencies[f] = (times, data)

    latencies['start'] = start

    return latencies

def listExperiments(directory):
    experiments = {}

    for expirementTag in listdir(directory):
        if (isdir(directory + "/" + expirementTag)):
            parts = re.match(MMTB_regexp, expirementTag)
            if parts:
                experiments[expirementTag] = {
                    'type': "MMTB",
                    'writers': int(parts.group(1)),
                    'readers': int(parts.group(2)),
                    'write_rate' : int(parts.group(3))
                }

            parts = re.match(MultiMMTB_regexp, expirementTag)
            if parts:
                experiments[expirementTag] = {
                    'type': "MultiMMTB",
                    'maps_per_writer': int(parts.group(1)),
                    'maps_per_reader': int(parts.group(2)),
                    'maps' : int(parts.group(3)),
                    'updates_per_second' : int(parts.group(4)),
                    'table_size' : int(parts.group(5))
                }

    return experiments