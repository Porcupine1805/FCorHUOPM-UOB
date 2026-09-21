#!/usr/bin/env python3
import argparse,csv,json,os,platform,random,subprocess,sys,time
from pathlib import Path

DEFAULT_MODES=["HUOPM","CORHUOPM_AC","CORHUOPM_BOND","COUPM_KULC_ADAPTED","UOB_POSTFILTER","UOB_FULL"]

def read_rows(path):
    with open(path,newline='',encoding='utf-8-sig') as f:return list(csv.DictReader(f))

def cmd_for(row,mode,heap,patterns=None,xms="512m",warmup_runs=0):
    return ["java",f"-Xms{xms}",f"-Xmx{heap}","-XX:+UseG1GC","-XX:+AlwaysPreTouch","-cp","build/classes",
      "org.fcorhuopm.SingleRun","--mode",mode,"--warmup-runs",str(warmup_runs),"--db",row["db"],"--utility",row["utility"],
      "--minsup",row["minsup"],"--minuo",row["minuo"],"--minac",row["minac"],
      "--minbond",row["minbond"],"--minkulc",row["minkulc"],"--minuob",row["minuob"]] + ([] if patterns is None else ["--patterns",str(patterns)])

def run_one(cmd,timeout):
    t=time.time();p=subprocess.run(cmd,text=True,capture_output=True,timeout=timeout)
    if p.returncode!=0:raise RuntimeError("Command failed:\n"+" ".join(cmd)+"\nSTDOUT:\n"+p.stdout+"\nSTDERR:\n"+p.stderr)
    lines=[x for x in p.stdout.splitlines() if x.strip().startswith("{")]
    if not lines:raise RuntimeError("No JSON result:\n"+p.stdout+"\n"+p.stderr)
    x=json.loads(lines[-1]);x["wall_ms"]=round((time.time()-t)*1000);return x,p.stdout,p.stderr

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("--config",default="config/selected9_grid.csv")
    ap.add_argument("--out",default="results/raw/selected9_raw.csv")
    ap.add_argument("--modes",default=",".join(DEFAULT_MODES))
    ap.add_argument("--heap",default=os.environ.get("JAVA_HEAP","4g"))
    ap.add_argument("--xms",default=os.environ.get("JAVA_XMS","512m"))
    ap.add_argument("--timeout",type=int,default=7200)
    ap.add_argument("--patterns-dir",default="")
    ap.add_argument("--override-repeats",type=int,default=0)
    ap.add_argument("--override-warmups",type=int,default=-1)
    args=ap.parse_args()
    root=Path(__file__).resolve().parents[1];os.chdir(root)
    rows=read_rows(args.config);modes=[x.strip() for x in args.modes.split(",") if x.strip()]
    out=Path(args.out);out.parent.mkdir(parents=True,exist_ok=True)
    pf=Path(args.patterns_dir) if args.patterns_dir else None
    if pf:pf.mkdir(parents=True,exist_ok=True)
    fields=["experiment_id","dataset","mode","repeat","transactions","retained_items","duplicate_transactions_normalized","minsup","minsup_count","minuo","mincorr",
       "preprocess_ms","runtime_ms","wall_ms","patterns","pattern_hash","peak_heap_mb","nodes","candidates","support_pruned",
       "correlation_pruned","uo_bound_pruned","eda_aborts","duo_merges","duo_entries_processed","duo_entries_materialized",
       "lazy_duo_skips","scratch_duo_merges","postfilter_entries_scanned","max_depth","java_heap","timestamp_utc"]
    new=not out.exists() or out.stat().st_size==0
    with open(out,"a",newline='',encoding='utf-8') as f:
      w=csv.DictWriter(f,fieldnames=fields); 
      if new:w.writeheader()
      for row in rows:
        reps=args.override_repeats or int(row.get("repeats") or 10)
        warm=int(row.get("warmups") or 1) if args.override_warmups<0 else args.override_warmups
        # Each measured observation runs in a fresh JVM. The requested warm-up
        # is executed inside that same JVM before the measured mining call, so JIT
        # compilation/cache effects are not confused with a different process.
        # Deterministic counterbalancing rotates/reverses method order.
        first={}
        for r in range(reps):
          order=modes[r%len(modes):]+modes[:r%len(modes)]
          if r%2:order=list(reversed(order))
          for mode in order:
            patt=(pf/f'{row["id"]}__{mode}.txt') if pf and r==0 else None
            print("run",row["id"],mode,r,flush=True)
            x,so,se=run_one(cmd_for(row,mode,args.heap,patt,args.xms,warm),args.timeout)
            rec={"experiment_id":row["id"],"dataset":row["dataset"],"mode":mode,"repeat":r,
                 **{k:x[k] for k in ["transactions","retained_items","duplicate_transactions_normalized","minsup","minsup_count","minuo","mincorr","preprocess_ms","runtime_ms","wall_ms","patterns","pattern_hash","peak_heap_mb","nodes","candidates","support_pruned","correlation_pruned","uo_bound_pruned","eda_aborts","duo_merges","duo_entries_processed","duo_entries_materialized","lazy_duo_skips","scratch_duo_merges","postfilter_entries_scanned","max_depth"]},
                 "java_heap":args.heap,"timestamp_utc":time.strftime("%Y-%m-%dT%H:%M:%SZ",time.gmtime())}
            w.writerow(rec);f.flush()
            if mode.startswith("UOB_"):
              key=(x["patterns"],x["pattern_hash"])
              if "uob" not in first:first["uob"]=key
              elif first["uob"]!=key:raise AssertionError(f'UOB semantic mismatch {row["id"]}: {first["uob"]} != {key}')
    print(out)

if __name__=="__main__":main()
