#!/usr/bin/python

import os
import sys
import time

overall_size = (1 << int(sys.argv[1]))

unit_size = (overall_size / int(sys.argv[2]))

size = unit_size

while size <= overall_size:
    for j in [1, 0]:
        for k in [1, 2, 4, 8, 16]:
            for i in xrange(0, 5):
                repeat_str = str(128)

                cmd1 = "java -Xmx32g -jar target/aes-machsuite-1.0-SNAPSHOT-jar-with-dependencies.jar input.txt output.txt " + str(size) + " " + repeat_str + " " + str(size) + " " + str(k)
                cmd2 = "with-sdaccel ../hls/bin/aes ../hls/aes-hw.xclbin " + str(size) + " 1 1 " + str(j)
                cmd3 = "pkill aes"
                print cmd2
                os.system(cmd2 + " |& tee -a exp_" + str(size) + ".log &");
                time.sleep(20)
                print cmd1
                os.system(cmd1 + " |& tee -a exp_" + str(size) + ".log")
                print cmd3
                os.system(cmd3 + " |& tee -a exp_" + str(size) + ".log")
                time.sleep(20)
    size = size + unit_size
