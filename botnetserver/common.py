import json

class Enum(set):
    def __getattr__(self, name):
        if name in self:
            return name
        raise AttributeError

VideoStatus = Enum(["NOTSENDING","SENDING","RECORDING"])

class Phone(object):
  def __init__(self, model, status, port, ip, servercommands, serial, videostatus):
    self.model = model
    self.status = status
    self.port = port
    self.ip = ip
    self.servercommands = servercommands
    self.serial = serial
    self.videostatus = videostatus
    self.videoscount = 0

def phoneStatusUpdate(p):
  return json.dumps(["phonestatusupdate",{"model": p.model, "serial": p.serial, "status": p.status, "videostatus": p.videostatus, "ip": p.ip, "port": p.port}])

def commandListUpdate(p):
  return json.dumps(["commandlistupdate", p.servercommands, p.serial])

def newVideo(path, p):
  return json.dumps(["newvideo", path, p.serial])
