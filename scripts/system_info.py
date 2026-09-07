#!/usr/bin/env python3
import json, os, platform, subprocess, sys, time
def cmd(x):
    try:return subprocess.check_output(x,text=True,stderr=subprocess.STDOUT).strip()
    except Exception as e:return f"unavailable: {e}"
info={
 "timestamp_utc":time.strftime("%Y-%m-%dT%H:%M:%SZ",time.gmtime()),
 "platform":platform.platform(),"machine":platform.machine(),"processor":platform.processor(),
 "python":sys.version.replace("\n"," "),"java":cmd(["java","-version"]),
 "javac":cmd(["javac","-version"]),"cpu_count":os.cpu_count(),
}
try:
 import psutil
 info["ram_bytes"]=psutil.virtual_memory().total
except Exception: pass
print(json.dumps(info,indent=2))
