package org.fcorhuopm;

import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

public final class Miner {
    static final double EPS=1e-12;
    private final PreparedDatabase db; private final Thresholds th; private final MiningMode mode;
    private final Metrics m=new Metrics(); private long patterns; private BufferedWriter out; private final MessageDigest dig;

    static final class State {
        final int last; final UOList uo; final DuoList duo; final int[] unionTids;
        final int maxSingletonSupport; final double invSupportSum; final int length;
        State(int last,UOList uo,DuoList duo,int[] unionTids,int maxSupp,double invSum,int len){
            this.last=last;this.uo=uo;this.duo=duo;this.unionTids=unionTids;this.maxSingletonSupport=maxSupp;this.invSupportSum=invSum;this.length=len;
        }
    }
    public record Result(MiningMode mode,long patterns,String patternHash,long runtimeMs,double peakHeapMb,
        long nodes,long candidates,long supportPruned,long correlationPruned,long uoBoundPruned,long edaAborts,
        long duoMerges,long duoEntriesProcessed,long duoEntriesMaterialized,long lazyDuoSkips,long scratchDuoMerges,
        long postfilterEntriesScanned,int maxDepth){}

    public Miner(PreparedDatabase db,Thresholds th,MiningMode mode){
        this.db=db;this.th=th;this.mode=mode;
        if(Math.abs(db.minSup-th.minSup())>1e-15) throw new IllegalArgumentException("Prepared DB minSup mismatch");
        try{dig=MessageDigest.getInstance("SHA-256");}catch(Exception e){throw new RuntimeException(e);}
    }

    public Result run(Path patternFile) throws IOException {
        if(patternFile!=null){Files.createDirectories(patternFile.toAbsolutePath().getParent());out=Files.newBufferedWriter(patternFile);}
        ArrayList<State> init=new ArrayList<>();
        for(int item:db.orderedItems){
            UOList s=db.singletons[item];
            DuoList d=needsIncrementalDuo()?DuoList.singleton(s):null;
            int[] uni=mode==MiningMode.CORHUOPM_BOND?s.tids:null;
            init.add(new State(item,s,d,uni,db.support[item],1.0/db.support[item],1));
        }
        PeakSampler sampler=new PeakSampler();
        long t0=System.nanoTime(); sampler.start();
        try{dfs(new int[0],null,init);}
        finally{sampler.stop();if(out!=null){out.flush();out.close();out=null;}}
        long ms=(System.nanoTime()-t0)/1_000_000L;
        return new Result(mode,patterns,hex(dig.digest()),ms,sampler.peak/1048576.0,m.nodesEvaluated,m.candidatesConstructed,
            m.supportPruned,m.correlationPruned,m.uoBoundPruned,m.edaAborts,m.duoMerges,m.duoEntriesProcessed,
            m.duoEntriesMaterialized,m.lazyDuoSkips,m.scratchDuoMerges,m.postfilterEntriesScanned,m.maxDepth);
    }

    private void dfs(int[] prefix,UOList prefixList,List<State> ext)throws IOException{
        for(int i=0;i<ext.size();i++){
            State x=ext.get(i);m.nodesEvaluated++;m.maxDepth=Math.max(m.maxDepth,x.length);
            int[] items=append(prefix,x.last);
            double corr=correlation(items,x);
            if(earlyCorr() && corr+EPS<th.correlation(mode)){m.correlationPruned++;continue;}
            double avg=x.uo.avg();boolean uoPass=avg+EPS>=th.minUo();boolean output=false;double outCorr=corr;
            switch(mode){
                case HUOPM -> output=uoPass;
                case CORHUOPM_AC,CORHUOPM_BOND -> output=uoPass; // passed early corr
                case COUPM_KULC_ADAPTED -> output=uoPass && corr+EPS>=th.minKulc(); // contextual output-filter baseline
                case UOB_POSTFILTER -> {if(uoPass){outCorr=uobPostFilterStreaming(items,x.uo.wAnd);output=outCorr+EPS>=th.minUob();}}
                default -> output=uoPass; // integrated UOB modes have passed early correlation
            }
            if(output)emit(items,x,avg,outCorr);
            if(useUoBound()&&upperBound(x.uo)+EPS<th.minUo()){m.uoBoundPruned++;continue;}
            ArrayList<State> children=new ArrayList<>();
            for(int j=i+1;j<ext.size();j++){State c=construct(prefixList,x,ext.get(j));if(c!=null)children.add(c);}
            if(!children.isEmpty())dfs(items,x.uo,children);
        }
    }

    private State construct(UOList prefix,State x,State y){
        m.candidatesConstructed++;
        if(mode==MiningMode.UOB_EAGER_DUO){
            // deliberately eager: denominator work before knowing whether the conjunctive child is frequent.
            UOList child=join(prefix,x.uo,y.uo,y.last);
            DuoList.MergeResult dr=DuoList.merge(x.duo,db.singletons[y.last],child.wAnd,th.minUob(),true,m);
            if(dr.rejected()){m.correlationPruned++;return null;}
            if(child.size()<db.minSupCount){m.supportPruned++;return null;}
            return new State(y.last,child,dr.list(),null,Math.max(x.maxSingletonSupport,db.support[y.last]),
                    x.invSupportSum+1.0/db.support[y.last],x.length+1);
        }
        UOList child=join(prefix,x.uo,y.uo,y.last);
        if(child.size()<db.minSupCount){m.supportPruned++;if(needsIncrementalDuo())m.lazyDuoSkips++;return null;}
        DuoList duo=null;int[] union=null;
        if(needsIncrementalDuo()){
            if(mode==MiningMode.UOB_RECOMPUTE_DUO){
                // State is intentionally recomputed from singleton lists for ablation.
                int[] itemset=new int[x.length+1]; // canonical path is reconstructed from UO state only via scratch denominator below
                // To preserve exact semantics, merge incremental child for recursion but score is recomputed at nodes.
                DuoList.MergeResult dr=DuoList.merge(x.duo,db.singletons[y.last],child.wAnd,th.minUob(),false,m);
                duo=dr.list();
            }else{
                DuoList.MergeResult dr=DuoList.merge(x.duo,db.singletons[y.last],child.wAnd,th.minUob(),useEda(),m);
                if(dr.rejected()){m.correlationPruned++;return null;}duo=dr.list();
            }
        } else if(mode==MiningMode.CORHUOPM_BOND){
            union=DuoList.unionTids(x.unionTids,db.singletons[y.last].tids);
        }
        return new State(y.last,child,duo,union,Math.max(x.maxSingletonSupport,db.support[y.last]),
                x.invSupportSum+1.0/db.support[y.last],x.length+1);
    }

    private UOList join(UOList prefix,UOList x,UOList y,int last){
        int cap=Math.min(x.size(),y.size());int[] t=new int[cap];double[] u=new double[cap],r=new double[cap],mn=new double[cap];
        int i=0,j=0,p=0,n=0;double sum=0,wand=0;
        while(i<x.size()&&j<y.size()){
            int a=x.tids[i],b=y.tids[j];if(a<b){i++;continue;}if(a>b){j++;continue;}
            double pu=0;if(prefix!=null){while(p<prefix.size()&&prefix.tids[p]<a)p++;if(p>=prefix.size()||prefix.tids[p]!=a){i++;j++;continue;}pu=prefix.uo[p];}
            double cu=x.uo[i]+y.uo[j]-pu, cm=Math.min(x.minUo[i],y.minUo[j]);
            t[n]=a;u[n]=cu;r[n]=y.ruo[j];mn[n]=cm;sum+=cu;wand+=cm;n++;i++;j++;
        }
        return new UOList(last,Arrays.copyOf(t,n),Arrays.copyOf(u,n),Arrays.copyOf(r,n),Arrays.copyOf(mn,n),sum,wand);
    }

    private double correlation(int[] items,State x){
        return switch(mode){
            case HUOPM,UOB_POSTFILTER -> 1.0;
            case CORHUOPM_AC -> x.uo.size()/(double)x.maxSingletonSupport;
            case CORHUOPM_BOND -> x.uo.size()/(double)x.unionTids.length;
            case COUPM_KULC_ADAPTED -> x.uo.size()*x.invSupportSum/x.length;
            case UOB_FULL,UOB_NO_EDA,UOB_NO_UO_BOUND,UOB_EAGER_DUO -> x.duo.sum<=EPS?0:x.uo.wAnd/x.duo.sum;
            case UOB_RECOMPUTE_DUO -> uobScratch(items,x.uo.wAnd);
        };
    }


    /**
     * Computes the UO-Bond denominator for a completed HUOP without materializing
     * intermediate disjunctive lists. This is the optimized post-filter baseline:
     * a k-way merge scans the singleton TID lists once and accumulates the maximum
     * singleton utility occupancy for each TID in the union.
     */
    private double uobPostFilterStreaming(int[] items,double wand){
        int k=items.length;
        int[] pos=new int[k];
        double denominator=0.0;
        while(true){
            int next=Integer.MAX_VALUE;
            boolean any=false;
            for(int j=0;j<k;j++){
                UOList a=db.singletons[items[j]];
                if(pos[j]<a.size()){
                    any=true;
                    if(a.tids[pos[j]]<next)next=a.tids[pos[j]];
                }
            }
            if(!any)break;
            double mx=0.0;
            for(int j=0;j<k;j++){
                UOList a=db.singletons[items[j]];
                if(pos[j]<a.size() && a.tids[pos[j]]==next){
                    mx=Math.max(mx,a.uo[pos[j]]);
                    pos[j]++;
                    m.postfilterEntriesScanned++;
                }
            }
            denominator+=mx;
        }
        return denominator<=EPS?0.0:wand/denominator;
    }

    private double uobScratch(int[] items,double wand){
        DuoList d=DuoList.singleton(db.singletons[items[0]]);
        for(int k=1;k<items.length;k++){m.scratchDuoMerges++;d=DuoList.unionExact(d,db.singletons[items[k]],m);}
        return d.sum<=EPS?0:wand/d.sum;
    }

    private double upperBound(UOList x){
        int k=db.minSupCount;if(x.size()<k)return 0;
        PriorityQueue<Double> q=new PriorityQueue<>(k);
        for(int i=0;i<x.size();i++){double v=x.uo[i]+x.ruo[i];if(q.size()<k)q.add(v);else if(v>q.peek()){q.poll();q.add(v);}}
        double s=0;for(double v:q)s+=v;return s/k;
    }

    private void emit(int[] items,State x,double avg,double corr)throws IOException{
        patterns++;ByteBuffer bb=ByteBuffer.allocate(4);
        for(int it:items){bb.clear();bb.putInt(it);dig.update(bb.array());}dig.update((byte)0x7f);
        if(out!=null){
            StringBuilder s=new StringBuilder();for(int it:items){if(s.length()>0)s.append(' ');s.append(it);}
            out.write(String.format(Locale.US,"%s #SUP:%d #UO:%.10f #CORR:%.10f #WAND:%.10f%n",s,x.uo.size(),avg,corr,x.uo.wAnd));
        }
    }
    private boolean earlyCorr(){return mode==MiningMode.CORHUOPM_AC||mode==MiningMode.CORHUOPM_BOND||
        mode==MiningMode.UOB_FULL||mode==MiningMode.UOB_NO_EDA||mode==MiningMode.UOB_NO_UO_BOUND||
        mode==MiningMode.UOB_EAGER_DUO||mode==MiningMode.UOB_RECOMPUTE_DUO;}
    private boolean needsIncrementalDuo(){return mode==MiningMode.UOB_FULL||mode==MiningMode.UOB_NO_EDA||
        mode==MiningMode.UOB_NO_UO_BOUND||mode==MiningMode.UOB_EAGER_DUO||mode==MiningMode.UOB_RECOMPUTE_DUO;}
    private boolean useEda(){return mode==MiningMode.UOB_FULL||mode==MiningMode.UOB_NO_UO_BOUND;}
    private boolean useUoBound(){return mode!=MiningMode.UOB_NO_UO_BOUND;}
    private static int[] append(int[] p,int x){int[] r=Arrays.copyOf(p,p.length+1);r[p.length]=x;return r;}
    private static long used(){Runtime r=Runtime.getRuntime();return r.totalMemory()-r.freeMemory();}
    private static final class PeakSampler {
        volatile boolean run; volatile long peak=used(); Thread t;
        void start(){run=true;t=new Thread(()->{while(run){long u=used();if(u>peak)peak=u;try{Thread.sleep(2);}catch(InterruptedException e){break;}}},"heap-sampler");t.setDaemon(true);t.start();}
        void stop(){run=false;if(t!=null){t.interrupt();try{t.join(50);}catch(InterruptedException e){Thread.currentThread().interrupt();}}long u=used();if(u>peak)peak=u;}
    }
    private static String hex(byte[] x){StringBuilder b=new StringBuilder();for(byte v:x)b.append(String.format("%02x",v));return b.toString();}
}