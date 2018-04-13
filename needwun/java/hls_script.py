#!/usr/bin/python

import os
import sys
import time

size_idx = int(sys.argv[2])

while size_idx >= int(sys.argv[1]):
    for i in xrange(0, 20):
        size = (1 << size_idx)
        repeat_str = str((1 << 32) / size)
        if int(repeat_str) > 8192:
            repeat_str = str(8192)
        cmd1 = "java -Xmx32g -jar target/wordcount-machsuite-1.0-SNAPSHOT-jar-with-dependencies.jar input.txt ease " + str(size) + " " + repeat_str + " " + str(size)
        cmd2 = "with-sdaccel ../hls/bin/workload ../hls/workload-hw.xclbin " + str(size)
        cmd3 = "pkill workload"
        print cmd2
        os.system(cmd2 + " |& tee -a exp_" + str(size) + ".log &");
        time.sleep(30)
        print cmd1
        os.system(cmd1 + " |& tee -a exp_" + str(size) + ".log")
        print cmd3
        os.system(cmd3 + " |& tee -a exp_" + str(size) + ".log")
        time.sleep(30)
    size_idx = size_idx - 1