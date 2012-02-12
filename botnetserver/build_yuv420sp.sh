#!/bin/sh
gcc -mtune=native -march=native -O3 -std=c99 -c c_yuv420sp.c && gcc -shared -Wl,-soname,c_yuv420sp -o c_yuv420sp.so c_yuv420sp.o
