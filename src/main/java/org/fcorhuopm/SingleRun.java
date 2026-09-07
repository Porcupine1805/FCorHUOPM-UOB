package org.fcorhuopm;

import java.nio.file.*;
import java.util.*;

public final class SingleRun {
    public static void main(String[] args)throws Exception{
        Map<String,String>a=parse(args);
        MiningMode mode=MiningMode.valueOf(req(a,"mode"));
        Path db=Path.of(req(a,"db")),ut=Path.of(req(a,"utility"));
        Thresholds th=new Thresholds(d(a,"minsup"),d(a,"minuo"),d0(a,"minac"),d0(a,"minbond"),d0(a,"minkulc"),d0(a,"minuob"));
        PreparedDatabase p=PreparedDatabase.load(db,ut,th.minSup());
        int warmups=Integer.parseInt(a.getOrDefault("warmup-runs","0"));
        for(int i=0;i<warmups;i++) new Miner(p,th,mode).run(null);
        System.gc(); Thread.sleep(25L);
        Path patterns=a.containsKey("patterns")?Path.of(a.get("patterns")):null;
        Miner.Result r=new Miner(p,th,mode).run(patterns);
        System.out.printf(Locale.US,
          "{\"mode\":\"%s\",\"transactions\":%d,\"retained_items\":%d,\"duplicate_transactions_normalized\":%d,\"minsup\":%.10f,\"minsup_count\":%d,\"minuo\":%.10f,\"mincorr\":%.10f,\"preprocess_ms\":%d,\"runtime_ms\":%d,\"patterns\":%d,\"pattern_hash\":\"%s\",\"peak_heap_mb\":%.6f,\"nodes\":%d,\"candidates\":%d,\"support_pruned\":%d,\"correlation_pruned\":%d,\"uo_bound_pruned\":%d,\"eda_aborts\":%d,\"duo_merges\":%d,\"duo_entries_processed\":%d,\"duo_entries_materialized\":%d,\"lazy_duo_skips\":%d,\"scratch_duo_merges\":%d,\"postfilter_entries_scanned\":%d,\"max_depth\":%d}%n",
          mode,p.transactions,p.retainedItems,p.duplicateTransactionsNormalized,th.minSup(),p.minSupCount,th.minUo(),th.correlation(mode),p.preprocessingMs,r.runtimeMs(),r.patterns(),r.patternHash(),r.peakHeapMb(),
          r.nodes(),r.candidates(),r.supportPruned(),r.correlationPruned(),r.uoBoundPruned(),r.edaAborts(),r.duoMerges(),r.duoEntriesProcessed(),
          r.duoEntriesMaterialized(),r.lazyDuoSkips(),r.scratchDuoMerges(),r.postfilterEntriesScanned(),r.maxDepth());
    }
    static Map<String,String>parse(String[]x){HashMap<String,String>m=new HashMap<>();for(int i=0;i<x.length;i++){if(!x[i].startsWith("--"))throw new IllegalArgumentException(x[i]);String k=x[i].substring(2);String v=(i+1<x.length&&!x[i+1].startsWith("--"))?x[++i]:"true";m.put(k,v);}return m;}
    static String req(Map<String,String>m,String k){if(!m.containsKey(k))throw new IllegalArgumentException("Missing --"+k);return m.get(k);}
    static double d(Map<String,String>m,String k){return Double.parseDouble(req(m,k));}
    static double d0(Map<String,String>m,String k){return Double.parseDouble(m.getOrDefault(k,"0"));}
}
