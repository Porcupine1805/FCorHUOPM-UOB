package org.fcorhuopm;

import java.io.*;
import java.util.*;

final class PairParser {
    int[] q=new int[32], item=new int[32]; int size;
    void parse(String s) throws IOException {
        size=0; String[] f=s.trim().split("\\s*,\\s*");
        if(f.length==0 || (f.length&1)!=0) throw new IOException("Invalid quantity,item transaction: "+s);
        ensure(f.length/2);
        for(int i=0;i<f.length;i+=2) {
            int qq=Integer.parseInt(f[i]), it=Integer.parseInt(f[i+1]);
            if(qq<0||it<0) throw new IOException("Negative quantity/item: "+s);
            int pos=-1;
            for(int j=0;j<size;j++) if(item[j]==it){pos=j;break;}
            if(pos>=0) q[pos]=Math.addExact(q[pos],qq);
            else { q[size]=qq; item[size]=it; size++; }
        }
    }
    void ensure(int n){if(n<=q.length)return;int c=q.length;while(c<n)c*=2;q=Arrays.copyOf(q,c);item=Arrays.copyOf(item,c);}
}