package org.fcorhuopm;

import java.util.*;

final class DuoList {
    final int[] tids;
    final double[] maxUo;
    final double sum;
    DuoList(int[] tids,double[] maxUo,double sum){this.tids=tids;this.maxUo=maxUo;this.sum=sum;}
    static DuoList singleton(UOList x){return new DuoList(x.tids,x.uo,x.sumUo);}

    record MergeResult(DuoList list, boolean rejected){}

    static MergeResult merge(DuoList a,UOList singleton,double numerator,double theta,
                             boolean eda,Metrics m) {
        m.duoMerges++;
        int max=a.tids.length+singleton.tids.length;
        int[] t=new int[Math.min(Math.max(8,Math.min(max,128)),Math.max(8,max))];
        double[] v=new double[t.length];
        int i=0,j=0,n=0; double sum=0;
        double limit=(eda&&theta>0)? numerator/theta : Double.POSITIVE_INFINITY;
        while(i<a.tids.length||j<singleton.tids.length) {
            int tid; double val;
            if(j>=singleton.tids.length || (i<a.tids.length&&a.tids[i]<singleton.tids[j])) {
                tid=a.tids[i];val=a.maxUo[i++];
            } else if(i>=a.tids.length || singleton.tids[j]<a.tids[i]) {
                tid=singleton.tids[j];val=singleton.uo[j++];
            } else {
                tid=a.tids[i];val=Math.max(a.maxUo[i],singleton.uo[j]);i++;j++;
            }
            m.duoEntriesProcessed++;
            sum+=val;
            if(eda && sum>limit+Miner.EPS) {m.edaAborts++; return new MergeResult(null,true);}
            if(n==t.length){int c=Math.min(max,Math.max(n+1,t.length*2));t=Arrays.copyOf(t,c);v=Arrays.copyOf(v,c);}
            t[n]=tid;v[n]=val;n++;
        }
        m.duoEntriesMaterialized+=n;
        return new MergeResult(new DuoList(Arrays.copyOf(t,n),Arrays.copyOf(v,n),sum),false);
    }

    static DuoList unionExact(DuoList a,UOList singleton,Metrics m){
        return merge(a,singleton,Double.POSITIVE_INFINITY,0,false,m).list();
    }

    static int[] unionTids(int[] a,int[] b) {
        int[] z=new int[a.length+b.length];int i=0,j=0,n=0;
        while(i<a.length||j<b.length){
            int x;
            if(j>=b.length||(i<a.length&&a[i]<b[j]))x=a[i++];
            else if(i>=a.length||b[j]<a[i])x=b[j++];
            else{x=a[i];i++;j++;}
            z[n++]=x;
        }
        return Arrays.copyOf(z,n);
    }
}