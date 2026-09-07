package org.fcorhuopm;

final class Metrics {
    long nodesEvaluated,candidatesConstructed,supportPruned,correlationPruned,uoBoundPruned;
    long duoMerges,duoEntriesProcessed,duoEntriesMaterialized,edaAborts,lazyDuoSkips,scratchDuoMerges;
    long postfilterEntriesScanned;
    int maxDepth;
}
