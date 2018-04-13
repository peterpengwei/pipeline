#!/usr/bin/python

import os
import sys
import time

size_idx = int(sys.argv[2])

while size_idx >= int(sys.argv[1]):
    for j in [1, 0]:
        for k in [1]:
            for i in xrange(0, 10):
                size = (1 << size_idx)
                ## repeat_str = str((1 << 32) / size)
                ## if int(repeat_str) > 8192:
                ##     repeat_str = str(8192)
                repeat_str = str(128)

                cmd1 = "java -Xmx32g -jar target/aes-machsuite-1.0-SNAPSHOT-jar-with-dependencies.jar input.txt output.txt " + str(size) + " " + repeat_str + " " + str(size)
                cmd2 = "with-sdaccel ../hls/bin/aes ../hls/bit/aes-hw.xclbin " + str(size) + " 1 " + str(k) + " " + str(j)
                cmd3 = "pkill aes"
                print cmd2
                os.system(cmd2 + " |& tee -a exp_" + str(size) + ".log &");
                time.sleep(20)
                print cmd1
                os.system(cmd1 + " |& tee -a exp_" + str(size) + ".log")
                print cmd3
                os.system(cmd3 + " |& tee -a exp_" + str(size) + ".log")
                time.sleep(20)
    size_idx = size_idx - 1
