#!/usr/bin/python

import os
import sys
import time
from numpy import average

results = [[],[],[],[],[],[],[],[],[],[]]
results[0].append("Size(MBs)")
results[1].append("Pack")
results[2].append("Put")
results[3].append("Gather")
results[4].append("Recv")
results[5].append("Unpack")
results[6].append("Overall")
results[7].append("Send")
results[8].append("Compute")
results[9].append("Scatter")


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
        packs.append(float(log_file.readline().strip().split()[-1]))
        singles.append(float(log_file.readline().strip().split()[-1]))
        sends.append(float(log_file.readline().strip().split()[-1]))
        recvs.append(float(log_file.readline().strip().split()[-1]))
        unpacks.append(float(log_file.readline().strip().split()[-1]))
        overalls.append(float(log_file.readline().strip().split()[-1]))
        log_file.readline()
        gathers.append(float(log_file.readline().strip().split()[-1]))
        computes.append(float(log_file.readline().strip().split()[-1]))
        scatters.append(float(log_file.readline().strip().split()[-1]))
    log_file.close()

    packs.sort()
    singles.sort()
    gathers.sort()
    recvs.sort()
    unpacks.sort()
    overalls.sort()
    sends.sort()
    scatters.sort()
    computes.sort()

    results[1].append(str(average(packs[5:15])))
    results[2].append(str(average(singles[5:15])))
    results[3].append(str(average(gathers[5:15])))
    results[4].append(str(average(recvs[5:15])))
    results[5].append(str(average(unpacks[5:15])))
    results[6].append(str(average(overalls[5:15])))
    results[7].append(str(average(sends[5:15])))
    results[8].append(str(average(computes[5:15])))
    results[9].append(str(average(scatters[5:15])))

    size_idx = size_idx + 1

output = open("final.csv", 'w')
for numbers in results:
    output.write(','.join(numbers) + '\n')
output.close()
