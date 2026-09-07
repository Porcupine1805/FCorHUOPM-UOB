#!/usr/bin/env python3
import argparse,csv,random,statistics,math
from collections import defaultdict
def main():
 ap=argparse.ArgumentParser();ap.add_argument("raw");ap.add_argument("--a",default="UOB_FULL");ap.add_argument("--b",default="UOB_POSTFILTER");ap.add_argument("--out",default="results/summary/paired_tests.csv");ap.add_argument("--permutations",type=int,default=20000);args=ap.parse_args()
 rows=list(csv.DictReader(open(args.raw,encoding="utf-8-sig")));idx=defaultdict(dict)
 for r in rows:idx[(r["experiment_id"],int(r["repeat"]))][r["mode"]]=r
 out=[];rng=random.Random(20260824)
 for exp in sorted(set(k[0] for k in idx)):
  pairs=[(float(v[args.a]["runtime_ms"]),float(v[args.b]["runtime_ms"])) for k,v in idx.items() if k[0]==exp and args.a in v and args.b in v]
  if not pairs:continue
  d=[a-b for a,b in pairs];obs=abs(statistics.mean(d));ge=0
  for _ in range(args.permutations):
   z=abs(sum(x*(1 if rng.random()<.5 else -1) for x in d)/len(d))
   if z>=obs-1e-12:ge+=1
  p=(ge+1)/(args.permutations+1)
  med=statistics.median(d)
  out.append([exp,args.a,args.b,len(d),statistics.mean(d),med,p])
 with open(args.out,"w",newline="") as f:
  w=csv.writer(f);w.writerow(["experiment_id","method_a","method_b","n","mean_runtime_difference_ms","median_runtime_difference_ms","paired_signflip_p"]);w.writerows(out)
if __name__=="__main__":main()
