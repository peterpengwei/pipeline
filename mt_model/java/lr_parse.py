#!/usr/bin/python

import os
import sys
import time
import math
from numpy import average

overall_size = (1 << int(sys.argv[1]))
unit_size = (overall_size / int(sys.argv[2]))
size = unit_size

results_all = [[],[],[],[],[],[]]
results_pcie = [[],[],[],[],[],[]]

while size <= overall_size:
    log_file = open("exp_" + str(size) + ".log")
    for j in [1, 0]:
        for k in [1]:
            overalls = []
            gathers = []
            OpenCLLoads = []
            scatters = []
            OpenCLStores = []
            computes = []
            for i in xrange(0, 5):
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
                results_all[0] = results_all[0] + map(lambda ele: (str(size), str(ele)), overalls)
                results_all[1] = results_all[1] + map(lambda ele: (str(size), str(ele)), gathers)
                results_all[2] = results_all[2] + map(lambda ele: (str(size), str(ele)), scatters)
                results_all[3] = results_all[3] + map(lambda ele: (str(size), str(ele)), OpenCLLoads)
                results_all[4] = results_all[4] + map(lambda ele: (str(size), str(ele)), computes)
                results_all[5] = results_all[5] + map(lambda ele: (str(size), str(ele)), OpenCLStores)
            else:
                results_pcie[0] = results_pcie[0] + map(lambda ele: (str(size), str(ele)), overalls)
                results_pcie[1] = results_pcie[1] + map(lambda ele: (str(size), str(ele)), gathers)
                results_pcie[2] = results_pcie[2] + map(lambda ele: (str(size), str(ele)), scatters)
                results_pcie[3] = results_pcie[3] + map(lambda ele: (str(size), str(ele)), OpenCLLoads)
                results_pcie[4] = results_pcie[4] + map(lambda ele: (str(size), str(ele)), computes)
                results_pcie[5] = results_pcie[5] + map(lambda ele: (str(size), str(ele)), OpenCLStores)

    log_file.close()
    size = size + unit_size

output = open("final_all.csv", 'w')

output.write('Overall' + '\n')
for ele in results_all[0]:
    output.write(','.join(ele) + '\n')
output.write('\n')

output.write('Gather' + '\n')
for ele in results_all[1]:
    output.write(','.join(ele) + '\n')
output.write('\n')

output.write('CLLoad' + '\n')
for ele in results_all[2]:
    output.write(','.join(ele) + '\n')
output.write('\n')

output.write('Compute' + '\n')
for ele in results_all[3]:
    output.write(','.join(ele) + '\n')
output.write('\n')

output.write('Scatter' + '\n')
for ele in results_all[4]:
    output.write(','.join(ele) + '\n')
output.write('\n')

output.write('CLStore' + '\n')
for ele in results_all[5]:
    output.write(','.join(ele) + '\n')
output.write('\n')

output.close()

output = open("final_pcie.csv", 'w')

output.write('Overall' + '\n')
for ele in results_pcie[0]:
    output.write(','.join(ele) + '\n')
output.write('\n')

output.write('Gather' + '\n')
for ele in results_pcie[1]:
    output.write(','.join(ele) + '\n')
output.write('\n')

output.write('CLLoad' + '\n')
for ele in results_pcie[2]:
    output.write(','.join(ele) + '\n')
output.write('\n')

output.write('Compute' + '\n')
for ele in results_pcie[3]:
    output.write(','.join(ele) + '\n')
output.write('\n')

output.write('Scatter' + '\n')
for ele in results_pcie[4]:
    output.write(','.join(ele) + '\n')
output.write('\n')

output.write('CLStore' + '\n')
for ele in results_pcie[5]:
    output.write(','.join(ele) + '\n')
output.write('\n')

output.close()
