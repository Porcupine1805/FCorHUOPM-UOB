#!/usr/bin/env python3
import itertools,subprocess,json,math,os,random,csv,tempfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];os.chdir(ROOT)
OUT=ROOT/"results/validation/randomized_validation.csv";OUT.parent.mkdir(parents=True,exist_ok=True)
rows=[];allchecks=0
for seed in range(20260801,20260821):
 rng=random.Random(seed);items=list(range(1,9));profit={i:rng.randint(1,15) for i in items};raw=[]
 for _ in range(45):
  chosen=[i for i in items if rng.random()<.42]
  if not chosen:chosen=[rng.choice(items)]
  raw.append({i:rng.randint(1,5) for i in chosen})
 db=ROOT/"results/validation"/f"rand_{seed}.txt";ut=ROOT/"results/validation"/f"rand_{seed}_utility.txt"
 db.write_text("\n".join(",".join(f"{q},{i}" for i,q in sorted(t.items())) for t in raw)+"\n")
 ut.write_text("\n".join(f"{i},{profit[i]}" for i in items)+"\n")
 tx=[]
 for t in raw:
  u={i:q*profit[i] for i,q in t.items()};tu=sum(u.values());tx.append((u,tu))
 def score(X):
  ands=[k for k,(u,tu) in enumerate(tx) if all(i in u for i in X)]
  ors=[k for k,(u,tu) in enumerate(tx) if any(i in u for i in X)]
  sup=len(ands)
  auo=sum(sum(tx[k][0][i] for i in X)/tx[k][1] for k in ands)/sup if sup else 0
  wa=sum(min(tx[k][0][i]/tx[k][1] for i in X) for k in ands)
  wo=sum(max(tx[k][0][i]/tx[k][1] for i in X if i in tx[k][0]) for k in ors)
  return sup,auo,(wa/wo if wo else 0)
 ms,mu,mc=.20,.25,.12;absmin=math.ceil(ms*len(tx))
 oracle=set();sc={}
 for r in range(1,len(items)+1):
  for X in itertools.combinations(items,r):
   z=score(X);sc[X]=z
   if z[0]>=absmin and z[1]+1e-12>=mu and z[2]+1e-12>=mc:oracle.add(X)
 checks=0
 for r in range(1,len(items)):
  for X in itertools.combinations(items,r):
   for y in items:
    if y not in X and y>max(X):
     Y=tuple(sorted(X+(y,)));checks+=1
     assert sc[Y][2] <= sc[X][2]+1e-11,(seed,X,Y,sc[X][2],sc[Y][2])
 allchecks+=checks
 patt=ROOT/"results/validation"/f"rand_{seed}_java.txt"
 cmd=["java","-Xms128m","-Xmx512m","-cp","build/classes","org.fcorhuopm.SingleRun","--mode","UOB_FULL","--db",str(db),"--utility",str(ut),
      "--minsup",str(ms),"--minuo",str(mu),"--minac","0","--minbond","0","--minkulc","0","--minuob",str(mc),"--patterns",str(patt)]
 p=subprocess.run(cmd,text=True,capture_output=True,check=True)
 got={tuple(sorted(map(int,line.split("#")[0].split()))) for line in patt.read_text().splitlines() if line.strip()}
 assert got==oracle,(seed,len(got),len(oracle),got^oracle)
 rows.append([seed,len(oracle),checks,"PASS"])
 db.unlink();ut.unlink();patt.unlink()
with open(OUT,"w",newline="") as f:
 w=csv.writer(f);w.writerow(["seed","patterns","one_item_extension_checks","status"]);w.writerows(rows)
print(f"PASS randomized exact regression: {len(rows)} seeds; anti-monotonicity checks={allchecks}")
print(OUT)
