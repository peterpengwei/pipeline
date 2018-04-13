#!/usr/bin/python

import os
import sys
import time
import math
from numpy import average

results_all = [[],[],[],[],[],[],[]]
results_all[0].append(["Size(MiB])"])
results_all[1].append(["Overall"])
results_all[2].append(["Gather"])
results_all[3].append(["OpenCLLoad"])
results_all[4].append(["Compute"])
results_all[5].append(["Scatter"])
results_all[6].append(["OpenCLStore"])

results_pcie = [[],[],[],[],[],[],[]]
results_pcie[0].append(["Size(MiB])"])
results_pcie[1].append(["Overall"])
results_pcie[2].append(["Gather"])
results_pcie[3].append(["OpenCLLoad"])
results_pcie[4].append(["Compute"])
results_pcie[5].append(["Scatter"])
results_pcie[6].append(["OpenCLStore"])

for i in xrange(int(sys.argv[1]), int(sys.argv[2])+1):
    for j in xrange(0, 7):
        results_all[j].append([])
        results_pcie[j].append([])

size_idx = int(sys.argv[1])

while size_idx <= int(sys.argv[2]):
    basic_idx = size_idx - int(sys.argv[1]) + 1
    size = (1 << size_idx)
    log_file = open("exp_" + str(size) + ".log")
    for j in [1, 0]:
        for k in [1]:
            actual_idx = int(basic_idx + math.log(k, 2))
            if ((1 << 32) / size) > 8192:
                size_str = str(size * 8192 / 1048576) + '_' + str(size_idx) + '_' + str(j) + '_' + str(k)
            else:
                size_str = "4096" + '_' + str(size_idx) + '_' + str(j) + '_' + str(k)
            if j == 1:
                results_all[0][basic_idx].append(size_str)
            else:
                results_pcie[0][basic_idx].append(size_str)
            overalls = []
            gathers = []
            OpenCLLoads = []
            scatters = []
            OpenCLStores = []
            computes = []
            for i in xrange(0, 10):
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
                computes.append(float(log_file.readline().strip().split()[-1]))
                gathers.append(float(log_file.readline().strip().split()[-1]))
                OpenCLLoads.append(float(log_file.readline().strip().split()[-1]))
                scatters.append(float(log_file.readline().strip().split()[-1]))
                OpenCLStores.append(float(log_file.readline().strip().split()[-1]))
                log_file.readline()
                log_file.readline()
                log_file.readline()
                log_file.readline()
                log_file.readline()
                log_file.readline()

            overalls.sort()
            computes.sort()
            gathers.sort()
            OpenCLLoads.sort()
            scatters.sort()
            OpenCLStores.sort()

            if j == 1:
                results_all[1][basic_idx] = results_all[1][basic_idx] + overalls
                results_all[2][basic_idx] = results_all[2][basic_idx] + gathers
                results_all[5][basic_idx] = results_all[5][basic_idx] + scatters
            else:
                results_pcie[1][basic_idx] = results_pcie[1][basic_idx] + overalls
                results_pcie[2][basic_idx] = results_pcie[2][basic_idx] + gathers
                results_pcie[5][basic_idx] = results_pcie[5][basic_idx] + scatters

            if actual_idx - 1 + int(sys.argv[1]) <= int(sys.argv[2]):
                if j == 1:
                    results_all[3][actual_idx] = results_all[3][actual_idx] + OpenCLLoads
                    results_all[4][actual_idx] = results_all[4][actual_idx] + computes
                    results_all[6][actual_idx] = results_all[6][actual_idx] + OpenCLStores
                else:
                    results_pcie[3][actual_idx] = results_pcie[3][actual_idx] + OpenCLLoads
                    results_pcie[4][actual_idx] = results_pcie[4][actual_idx] + computes
                    results_pcie[6][actual_idx] = results_pcie[6][actual_idx] + OpenCLStores

    log_file.close()
    size_idx = size_idx + 1

final_all = [[],[],[],[],[],[],[]]
final_all[0].append("Size(MiB)")
final_all[1].append("Overall")
final_all[2].append("Gather")
final_all[3].append("OpenCLLoad")
final_all[4].append("Compute")
final_all[5].append("Scatter")
final_all[6].append("OpenCLStore")

final_pcie = [[],[],[],[],[],[],[]]
final_pcie[0].append("Size(MiB)")
final_pcie[1].append("Overall")
final_pcie[2].append("Gather")
final_pcie[3].append("OpenCLLoad")
final_pcie[4].append("Compute")
final_pcie[5].append("Scatter")
final_pcie[6].append("OpenCLStore")

for i in xrange(int(sys.argv[1]), int(sys.argv[2])+1):
    basic_idx = i - int(sys.argv[1]) + 1
    for j in xrange(0, 7):
        if (j == 0):
            final_all[j].append(results_all[j][basic_idx][0])
            final_pcie[j].append(results_pcie[j][basic_idx][0])
        else:
            numbers = sorted(results_all[j][basic_idx])
            assert len(numbers) == 10
            final_all[j].append(str(average(numbers[2:7])))

            numbers = sorted(results_pcie[j][basic_idx])
            assert len(numbers) == 10
            final_pcie[j].append(str(average(numbers[2:7])))

output = open("final_all.csv", 'w')
for numbers in final_all:
    output.write(','.join(numbers) + '\n')
output.close()

output = open("final_pcie.csv", 'w')
for numbers in final_pcie:
    output.write(','.join(numbers) + '\n')
output.close()
