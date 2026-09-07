package org.fcorhuopm;

import java.util.*;

final class UOList {
    final int lastItem;
    final int[] tids;
    final double[] uo, ruo, minUo;
    final double sumUo, wAnd;
    UOList(int lastItem,int[] tids,double[] uo,double[] ruo,double[] minUo,double sumUo,double wAnd){
        this.lastItem=lastItem;this.tids=tids;this.uo=uo;this.ruo=ruo;this.minUo=minUo;this.sumUo=sumUo;this.wAnd=wAnd;
    }
    int size(){return tids.length;}
    double avg(){return size()==0?0:sumUo/size();}

    static final class Builder {
        final int item; int[] t; double[] u,r; int n; double sum;
        Builder(int item,int cap){this.item=item;cap=Math.max(4,cap);t=new int[cap];u=new double[cap];r=new double[cap];}
        void add(int tid,double x,double y){grow(n+1);t[n]=tid;u[n]=x;r[n]=y;n++;sum+=x;}
        private void grow(int m){if(m<=t.length)return;int c=Math.max(m,t.length*2);t=Arrays.copyOf(t,c);u=Arrays.copyOf(u,c);r=Arrays.copyOf(r,c);}
        UOList build(){int[] tt=Arrays.copyOf(t,n);double[] uu=Arrays.copyOf(u,n),rr=Arrays.copyOf(r,n);return new UOList(item,tt,uu,rr,Arrays.copyOf(uu,n),sum,sum);}
    }
}