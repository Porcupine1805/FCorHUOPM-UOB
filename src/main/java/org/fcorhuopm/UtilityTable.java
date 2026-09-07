package org.fcorhuopm;

import java.io.*;
import java.nio.file.*;
import java.util.*;

final class UtilityTable {
    private double[] u = new double[32];
    private boolean[] has = new boolean[32];
    int zeroUtilityItems=0;

    static UtilityTable load(Path p) throws IOException {
        UtilityTable t=new UtilityTable();
        try(BufferedReader br=Files.newBufferedReader(p)) {
            String s;
            while((s=br.readLine())!=null) {
                s=s.trim(); if(s.isEmpty()||s.startsWith("#")||s.startsWith("%")||s.startsWith("@")) continue;
                String[] a=s.split("\\s*,\\s*");
                if(a.length<2) throw new IOException("Invalid utility table: "+s);
                int item=Integer.parseInt(a[0]); double v=Double.parseDouble(a[1]);
                if(item<0 || !Double.isFinite(v) || v<0) throw new IOException("Invalid utility row: "+s);
                t.ensure(item+1);
                if(!t.has[item] && v==0) t.zeroUtilityItems++;
                t.u[item]=v; t.has[item]=true;
            }
        }
        return t;
    }
    double get(int item) throws IOException {
        if(item<0||item>=has.length||!has[item]) throw new IOException("Missing utility for item "+item);
        return u[item];
    }
    boolean positive(int item){return item>=0&&item<has.length&&has[item]&&u[item]>0;}
    int capacity(){return u.length;}
    private void ensure(int n){if(n<=u.length)return;int c=u.length;while(c<n)c*=2;u=Arrays.copyOf(u,c);has=Arrays.copyOf(has,c);}
}