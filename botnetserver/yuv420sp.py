from __future__ import print_function
import os
import array
from ctypes import *

  
def decodeyuv420sp(yuv420sp, w, h):
    so = CDLL("./c_yuv420sp.so")
    rgb = create_string_buffer(w*h*sizeof(c_int))
    so.decodeYUV420SP(rgb, yuv420sp, w, h)
    return rgb

"""
def decodeyuv420sp(yuv420sp, w, h):
    def clamp(minimum, x, maximum):
      return max(minimum, min(x, maximum))
  
    rgb = array.array("L", [0]*(w*h))
    framesize = w*h;
    yp = 0;
    for j in range(0,h):
      uvp = framesize + (j >> 1) * w
      u = 0
      v = 0
      for i in range(0, w):
        y = (0xff & yuv420sp[yp]) - 16
        if (y < 0): y = 0
        if (i & 1) == 0:
          v = (0xff & yuv420sp[uvp]) - 128;
          uvp += 1
          u = (0xff & yuv420sp[uvp]) - 128
          uvp += 1
        
        y1192 = 1192 * y
        r = y1192 + 1634 * v
        g = y1192 - 833 * v - 400 * u
        b = y1192 + 2066 * u
        
        r = clamp(0,r,262143)
        g = clamp(0,g,262143)
        b = clamp(0,b,262143)
        
        rgb[yp] = 0xff000000 | ((r << 6) & 0xff0000) | ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);
  
        yp += 1
    return rgb
"""
  
def yuvtoimg(uchararr, w, h): 
  longarr1 = decodeyuv420sp(uchararr, w, h)
  
  def ROR(x, n, bits = 32):
      mask = (long(2)**n) - 1
      mask_bits = x & mask
      return (x >> n) | (mask_bits << (bits - n))
  
  def ROL(x, n, bits=32):
      return ROR(x, bits - n, bits)
  
  #longlist = [ROL(x,8) for x in longarr1]
  
  #longarr2 = array.array("L")
  #longarr2.fromlist(longlist)
  #print([hex(x) for x in a])
  longarr2 = longarr1
  
  from PIL import Image
  pilImage = Image.frombuffer('RGBA',(w,h),longarr2,'raw','RGBA',0,1)
  return pilImage
  
if __name__ == "__main__":
  #f = open("f1.yuv","rb")
  #uchararr = array.array("B")
  #uchararr.fromfile(f, os.stat("f1.yuv").st_size)

  f = open("f1.yuv","rb")
  uchararr = f.read()

  w = 768
  h = 432
  pilImage = yuvtoimg(uchararr, w, h)
  pilImage.save('my.png')
  print("saved to my.png")
