package org.fcorhuopm;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Structural validator for the supplied quantitative utility datasets. */
public final class DatasetValidator {
 public static void main(String[]a)throws Exception{
   if(a.length!=2)throw new IllegalArgumentException("DatasetValidator db utilityTable");
   UtilityTable u=UtilityTable.load(Path.of(a[1]));PairParser p=new PairParser();
   long tx=0,occ=0;int max=0,dupTx=0;Set<Integer>items=new HashSet<>();
   int[] seen=new int[u.capacity()];int sv=1;
   try(BufferedReader br=Files.newBufferedReader(Path.of(a[0]))){String s;
     while((s=br.readLine())!=null){
       s=s.trim();if(s.isEmpty()||s.startsWith("#")||s.startsWith("%")||s.startsWith("@"))continue;
       p.parse(s);if(++sv==Integer.MAX_VALUE){Arrays.fill(seen,0);sv=1;}
       double tu=0;boolean dup=false;
       for(int i=0;i<p.size;i++){
         if(p.q[i]<0)throw new IOException("Negative quantity tx "+tx);
         tu+=p.q[i]*u.get(p.item[i]);items.add(p.item[i]);occ++;
         if(seen[p.item[i]]==sv)dup=true;else seen[p.item[i]]=sv;
       }
       if(dup)dupTx++;
       if(!(tu>0))throw new IOException("TU<=0 tx "+tx);
       max=Math.max(max,p.size);tx++;
     }
   }
   System.out.printf(Locale.US,
     "VALID tx=%d items=%d occurrences=%d avg_len=%.6f max_len=%d duplicate_tx=%d utility_zero_items=%d%n",
     tx,items.size(),occ,occ/(double)tx,max,dupTx,u.zeroUtilityItems);
 }
}
