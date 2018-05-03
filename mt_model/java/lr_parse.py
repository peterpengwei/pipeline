#!/usr/bin/python

import os
import sys
import time
import math
from numpy import average

overall_size = (1 << int(sys.argv[1]))
unit_size = (overall_size / int(sys.argv[2]))
size = unit_size

header = ['BatchSize', 'Overall', 'Thread', 'CPU', 'FPGA', 'Compute', 'SocketSend', 'CLLoad', 'SocketRecv', 'CLStore']

output = open("final_all.csv", 'w')
output.write(','.join(header) + '\n')

while size <= overall_size:
    log_file = open("exp_" + str(size) + ".log")
    for j in [1]:
        for k in [1, 2, 4, 8, 16]:
            for i in xrange(0, 5):
                log_file.readline()
                log_file.readline()
                log_file.readline()
                log_file.readline()
                overalls = (float(log_file.readline().strip().split()[-1]))
                cpu_fpga = log_file.readline().strip().split()
                cpu_jobs = (int(cpu_fpga[2][0:-1]))
                fpga_jobs = (int(cpu_fpga[5]))
                log_file.readline()
                log_file.readline()
                computes = (float(log_file.readline().strip().split()[-1]))
                gathers = (float(log_file.readline().strip().split()[-1]))
                OpenCLLoads = (float(log_file.readline().strip().split()[-1]))
                scatters = (float(log_file.readline().strip().split()[-1]))
                OpenCLStores = (float(log_file.readline().strip().split()[-1]))
                log_file.readline()
                log_file.readline()
                log_file.readline()
                log_file.readline()
                log_file.readline()
                log_file.readline()

                record = [str(size), str(overalls), str(k), str(cpu_jobs), str(fpga_jobs), str(computes), str(gathers), str(OpenCLLoads), str(scatters), str(OpenCLStores)]
                output.write(','.join(record) + '\n')

            ## overalls.sort()
            ## computes.sort()
            ## gathers.sort()
            ## OpenCLLoads.sort()
            ## scatters.sort()
            ## OpenCLStores.sort()

    log_file.close()
    size = size + unit_size

output.close()
