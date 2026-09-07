package org.fcorhuopm;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Support-threshold-specific vertical database.
 *
 * Duplicate item identifiers inside one transaction are normalized by aggregating
 * their utility before a singleton vertical entry is created. This is essential
 * for datasets such as Kosarak, where a small number of cleaned rows can still
 * contain repeated item identifiers.
 */
public final class PreparedDatabase {
    public final Path database, utilityTable;
    public final int transactions,minSupCount,retainedItems,zeroUtilityItemsFiltered,duplicateTransactionsNormalized;
    public final double minSup;
    public final long preprocessingMs;
    final int[] support,rank,orderedItems;
    final UOList[] singletons;

    private PreparedDatabase(Path d,Path u,int n,int m,double s,long ms,int zero,int dup,
                             int[] supp,int[] rank,int[] items,UOList[] singles){
        database=d;utilityTable=u;transactions=n;minSupCount=m;minSup=s;preprocessingMs=ms;
        zeroUtilityItemsFiltered=zero;duplicateTransactionsNormalized=dup;
        support=supp;this.rank=rank;orderedItems=items;singletons=singles;retainedItems=items.length;
    }

    public static PreparedDatabase load(Path db,Path ut,double minSup) throws IOException {
        long st=System.nanoTime();
        UtilityTable ext=UtilityTable.load(ut);
        int cap=ext.capacity();
        int[] supp=new int[cap],stamp=new int[cap];
        PairParser pp=new PairParser();int n=0,sv=1,duplicates=0;

        // First scan: transaction utilities + de-duplicated singleton support.
        try(BufferedReader br=Files.newBufferedReader(db)){String s;
            while((s=br.readLine())!=null){
                s=s.trim();if(skip(s))continue;pp.parse(s);
                if(++sv==Integer.MAX_VALUE){Arrays.fill(stamp,0);sv=1;}
                double tu=0; boolean dup=false;
                for(int k=0;k<pp.size;k++){
                    int item=pp.item[k], q=pp.q[k];
                    if(q<0) throw new IOException("Negative quantity at row "+n);
                    double eu=ext.get(item);
                    tu+=q*eu;
                    if(q>0&&eu>0){
                        if(stamp[item]!=sv){stamp[item]=sv;supp[item]++;}
                        else dup=true;
                    }
                }
                if(dup)duplicates++;
                if(!(tu>0))throw new IOException("Non-positive transaction utility after cleaning at row "+n);
                n++;
            }
        }

        int minCount=(int)Math.ceil(minSup*n-1e-12);
        ArrayList<Integer> keep=new ArrayList<>();
        for(int i=0;i<supp.length;i++) if(ext.positive(i)&&supp[i]>=minCount) keep.add(i);
        keep.sort((a,b)->{int c=Integer.compare(supp[a],supp[b]);return c!=0?c:Integer.compare(a,b);});
        int[] items=keep.stream().mapToInt(Integer::intValue).toArray();
        int[] rank=new int[supp.length];Arrays.fill(rank,-1);
        for(int i=0;i<items.length;i++)rank[items[i]]=i;
        UOList.Builder[] bld=new UOList.Builder[supp.length];
        for(int it:items)bld[it]=new UOList.Builder(it,supp[it]);

        // Primitive stamp/value buffers normalize duplicate item IDs without per-row HashMaps.
        int[] active=new int[Math.max(128,Math.min(cap,4096))];
        int[] seen=new int[cap]; int seenVersion=1;
        double[] utilByItem=new double[cap];

        int tid=0;
        try(BufferedReader br=Files.newBufferedReader(db)){String s;
            while((s=br.readLine())!=null){
                s=s.trim();if(skip(s))continue;pp.parse(s);
                if(++seenVersion==Integer.MAX_VALUE){Arrays.fill(seen,0);seenVersion=1;}
                double tu=0;int rn=0;
                for(int k=0;k<pp.size;k++){
                    int item=pp.item[k],q=pp.q[k]; double eu=ext.get(item),uu=q*eu;
                    tu+=uu;
                    if(uu>0&&item<rank.length&&rank[item]>=0){
                        if(seen[item]!=seenVersion){
                            seen[item]=seenVersion;utilByItem[item]=uu;
                            if(rn==active.length)active=Arrays.copyOf(active,active.length*2);
                            active[rn++]=item;
                        } else utilByItem[item]+=uu;
                    }
                }
                if(!(tu>0))throw new IOException("Non-positive transaction utility in second scan at tid "+tid);

                // Canonical extension order (support ascending, then item ID).
                for(int i=1;i<rn;i++){
                    int it=active[i],rr=rank[it],j=i-1;
                    while(j>=0&&rank[active[j]]>rr){active[j+1]=active[j];j--;}
                    active[j+1]=it;
                }
                double rem=0;for(int i=0;i<rn;i++)rem+=utilByItem[active[i]];
                for(int i=0;i<rn;i++){
                    int it=active[i];double uu=utilByItem[it];rem-=uu;
                    bld[it].add(tid,uu/tu,rem/tu);
                }
                tid++;
            }
        }

        UOList[] singles=new UOList[supp.length];
        for(int it:items)singles[it]=bld[it].build();
        long ms=(System.nanoTime()-st)/1_000_000L;
        return new PreparedDatabase(db,ut,n,minCount,minSup,ms,ext.zeroUtilityItems,duplicates,supp,rank,items,singles);
    }
    private static boolean skip(String s){return s.isEmpty()||s.startsWith("#")||s.startsWith("%")||s.startsWith("@");}
}
