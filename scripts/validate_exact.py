#!/usr/bin/env python3
# Deterministic exhaustive validation against Java for the 10-transaction running database.
import itertools,subprocess,json,math,os
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];os.chdir(ROOT)
items=[1,2,3,4,5];profits={1:2,2:6,3:3,4:8,5:10}
raw=[{2:2,3:5,4:4},{1:4,2:6},{2:3,3:6,5:4},{1:1,2:2,3:7},{1:2,3:8},{1:6,2:5,4:4},{1:4,2:4,4:7,5:3},{2:2,3:3},{4:3,5:3},{4:2}]
tx=[]
for t in raw:
 u={i:q*profits[i] for i,q in t.items()};tu=sum(u.values());tx.append((u,tu))
def scores(X):
 andset=[k for k,(u,tu) in enumerate(tx) if all(i in u for i in X)]
 orset=[k for k,(u,tu) in enumerate(tx) if any(i in u for i in X)]
 sup=len(andset)
 avg=sum(sum(tx[k][0][i] for i in X)/tx[k][1] for k in andset)/sup if sup else 0
 wand=sum(min(tx[k][0][i]/tx[k][1] for i in X) for k in andset)
 wor=sum(max(tx[k][0][i]/tx[k][1] for i in X if i in tx[k][0]) for k in orset)
 return sup,avg,(wand/wor if wor else 0)
def oracle(ms=.2,mo=.3,mc=.15):
 return {tuple(X) for r in range(1,6) for X in itertools.combinations(items,r) if scores(X)[0]>=math.ceil(ms*len(tx)) and scores(X)[1]+1e-12>=mo and scores(X)[2]+1e-12>=mc}
def java(mode):
 cmd=["java","-Xms256m","-Xmx1g","-cp","build/classes","org.fcorhuopm.SingleRun","--mode",mode,"--db","datasets/toy/toy_db.txt","--utility","datasets/toy/toy_utility.txt","--minsup",".2","--minuo",".3","--minac",".3","--minbond",".3","--minkulc",".3","--minuob",".15","--patterns",f"results/validation/toy_{mode}.txt"]
 p=subprocess.run(cmd,text=True,capture_output=True,check=True);return json.loads([x for x in p.stdout.splitlines() if x.startswith("{")][-1])
exp=oracle()
for mode in ["UOB_POSTFILTER","UOB_FULL","UOB_NO_EDA","UOB_NO_UO_BOUND","UOB_EAGER_DUO","UOB_RECOMPUTE_DUO"]:
 x=java(mode);got=set()
 for line in open(f"results/validation/toy_{mode}.txt"):
  got.add(tuple(sorted(map(int,line.split("#")[0].strip().split()))))
 assert got==exp,(mode,got^exp)
 print("PASS",mode,len(got),x["pattern_hash"])
# algebraic anti-monotonicity on all one-item extensions
checks=0
for r in range(1,5):
 for X in itertools.combinations(items,r):
  ux=scores(X)[2]
  for y in items:
   if y not in X:
    Y=tuple(sorted(X+(y,)));checks+=1
    assert scores(Y)[2] <= ux+1e-12,(X,Y,ux,scores(Y)[2])
print("PASS anti-monotonicity checks",checks)
