#!/usr/bin/env python3
import argparse,csv,math,statistics
from collections import defaultdict
from pathlib import Path

def quantile(xs,q):
    a=sorted(xs);n=len(a)
    if n==1:return a[0]
    p=(n-1)*q;i=int(math.floor(p));j=min(i+1,n-1);return a[i]+(a[j]-a[i])*(p-i)
# two-sided 95% Student t multipliers for df 1..30, normal afterward
T95=[None,12.706,4.303,3.182,2.776,2.571,2.447,2.365,2.306,2.262,2.228,2.201,2.179,2.160,2.145,2.131,2.120,2.110,2.101,2.093,2.086,2.080,2.074,2.069,2.064,2.060,2.056,2.052,2.048,2.045,2.042]
def ci95(xs):
    if len(xs)<2:return (xs[0],xs[0])
    mean=statistics.mean(xs);sd=statistics.stdev(xs);df=len(xs)-1;t=T95[df] if df<len(T95) else 1.96
    h=t*sd/math.sqrt(len(xs));return mean-h,mean+h

def main():
    ap=argparse.ArgumentParser();ap.add_argument("raw");ap.add_argument("out");args=ap.parse_args()
    with open(args.raw,newline='',encoding='utf-8') as f:rows=list(csv.DictReader(f))
    g=defaultdict(list)
    for r in rows:g[(r["experiment_id"],r["dataset"],r["mode"])].append(r)
    fields=["experiment_id","dataset","mode","n","patterns","pattern_hash","runtime_mean_ms","runtime_sd_ms","runtime_median_ms","runtime_iqr_ms","runtime_ci95_lo_ms","runtime_ci95_hi_ms",
        "heap_mean_mb","heap_sd_mb","heap_median_mb","nodes_median","candidates_median","corr_pruned_median","uo_bound_pruned_median","eda_aborts_median","duo_entries_median","lazy_duo_skips_median"]
    Path(args.out).parent.mkdir(parents=True,exist_ok=True)
    with open(args.out,"w",newline='',encoding='utf-8') as f:
      w=csv.DictWriter(f,fieldnames=fields);w.writeheader()
      for k,rs in sorted(g.items()):
        rt=[float(x["runtime_ms"]) for x in rs];hp=[float(x["peak_heap_mb"]) for x in rs];lo,hi=ci95(rt)
        val=lambda key:statistics.median(float(x[key]) for x in rs)
        w.writerow(dict(experiment_id=k[0],dataset=k[1],mode=k[2],n=len(rs),patterns=rs[0]["patterns"],pattern_hash=rs[0]["pattern_hash"],
            runtime_mean_ms=statistics.mean(rt),runtime_sd_ms=(statistics.stdev(rt) if len(rt)>1 else 0),runtime_median_ms=statistics.median(rt),
            runtime_iqr_ms=quantile(rt,.75)-quantile(rt,.25),runtime_ci95_lo_ms=lo,runtime_ci95_hi_ms=hi,
            heap_mean_mb=statistics.mean(hp),heap_sd_mb=(statistics.stdev(hp) if len(hp)>1 else 0),heap_median_mb=statistics.median(hp),
            nodes_median=val("nodes"),candidates_median=val("candidates"),corr_pruned_median=val("correlation_pruned"),
            uo_bound_pruned_median=val("uo_bound_pruned"),eda_aborts_median=val("eda_aborts"),duo_entries_median=val("duo_entries_materialized"),
            lazy_duo_skips_median=val("lazy_duo_skips")))
if __name__=="__main__":main()
