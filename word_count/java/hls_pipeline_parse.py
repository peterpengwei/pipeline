#!/usr/bin/python

import os
import sys
import time
from numpy import average

results = [[],[],[],[],[],[],[],[],[],[]]
results[0].append("Size(MBs)")
results[1].append("Overall")
results[2].append("Gather")
results[3].append("Compute")
results[4].append("Scatter")


size_idx = int(sys.argv[1])

while size_idx <= int(sys.argv[2]):
    size = (1 << size_idx)
    if ((1 << 32) / size) > 8192:
        results[0].append(str(size * 8192 / 1048576))
    else:
        results[0].append("4096")
    log_file = open("exp_" + str(size) + ".log")
    packs = []
    singles = []
    sends = []
    recvs = []
    unpacks = []
    overalls = []
    gathers = []
    scatters = []
    computes = []
    for i in xrange(0, 20):
        log_file.readline()
        log_file.readline()
        log_file.readline()
        log_file.readline()
        log_file.readline()
        log_file.readline()
        log_file.readline()
        log_file.readline()
        log_file.readline()
        log_file.readline()
        overalls.append(float(log_file.readline().strip().split()[-1]))
        log_file.readline()
        log_file.readline()
        log_file.readline()
        log_file.readline()
        gathers.append(float(log_file.readline().strip().split()[-1]))
        log_file.readline()
        computes.append(float(log_file.readline().strip().split()[-1]))
        log_file.readline()
        log_file.readline()
        log_file.readline()
        scatters.append(float(log_file.readline().strip().split()[-1]))
    log_file.close()

    gathers.sort()
    overalls.sort()
    scatters.sort()
    computes.sort()

    results[1].append(str(average(overalls[5:15])))
    results[2].append(str(average(gathers[5:15])))
    results[3].append(str(average(computes[5:15])))
    results[4].append(str(average(scatters[5:15])))

    size_idx = size_idx + 1

output = open("final.csv", 'w')
for numbers in results:
    output.write(','.join(numbers) + '\n')
output.close()
