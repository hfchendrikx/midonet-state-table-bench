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

BASEDIR = "final-raw-data/writerate-transitionpoint/exp"

#experiments = ['MultiMMTB-20mpw20mpr2000maps8ups1000ts2400x','MultiMMTB-20mpw20mpr2000maps9ups1000ts2700x','MultiMMTB-20mpw20mpr2000maps10ups1000ts3000x', 'MultiMMTB-20mpw20mpr2000maps12ups1000ts3600x']

#if not experiments:
experiments = listExperiments(BASEDIR)

experiments_keys = [
    'MultiMMTB-20mpw20mpr2000maps5ups1000ts1500x',
    'MultiMMTB-20mpw20mpr2000maps6ups1000ts1800x',
    'MultiMMTB-20mpw20mpr2000maps7ups1000ts2100x',
    'MultiMMTB-20mpw20mpr2000maps8ups1000ts2400x',
    'MultiMMTB-20mpw20mpr2000maps9ups1000ts2700x',
    'MultiMMTB-20mpw20mpr2000maps10ups1000ts3000x',
    'MultiMMTB-20mpw20mpr2000maps12ups1000ts3600x',
    'MultiMMTB-20mpw20mpr2000maps14ups1000ts4200x',
    'MultiMMTB-20mpw20mpr2000maps16ups1000ts4800x'
]


print experiments
width = 3#int(np.ceil(sqrt(len(experiments))))
height = 3#int(np.ceil(len(experiments) / float(width)))
x = 0;
y = 0;

print "width: " + str(width) + ", height:" + str(height)
fig, ax = plt.subplots(width,height, sharex=True, sharey=True)
#plt.subplots_adjust(left=0.03, bottom=0.02, right=0.98, top=0.98, wspace=0.02, hspace=0.02)

for experiment in experiments_keys:

    if (experiments[experiment]['updates_per_second'] < 5 or experiments[experiment]['updates_per_second'] > 16):
        continue

    print experiment
    EXPERIMENT_DIR = BASEDIR + "/" + experiment
    all_latencies = list()

    for f in listdir(EXPERIMENT_DIR):
        if (isdir(EXPERIMENT_DIR + "/" + f)):
            filename = EXPERIMENT_DIR + "/" + f + "/raw-latency-data"
            if isfile(filename):
                data = processDataFile(filename)
                all_latencies = all_latencies + data


    ax[x,y].hist(all_latencies, histtype="stepfilled",
                 bins=100, alpha=0.5, normed=True, range=(0,1500))

    ax[x,y].set_title(str(experiments[experiment]['updates_per_second'] * experiments[experiment]['maps']) + " ups")
    ax[x,y].set_xlim(0,1500)
    ax[x,y].set_ylim(0, 0.02)

    if x == height-1:
        ax[x,y].set_xlabel("Latency [ms]")

    if y == 0:
        ax[x,y].set_ylabel("Frequency")

    x += 1
    if x >= width:
        x = 0
        y += 1



plt.show()