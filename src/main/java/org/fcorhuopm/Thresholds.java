package org.fcorhuopm;

public record Thresholds(double minSup, double minUo, double minAc,
                         double minBond, double minKulc, double minUob) {
    public Thresholds {
        if (!(minSup > 0 && minSup <= 1)) throw new IllegalArgumentException("minSup in (0,1]");
        check(minUo,"minUo"); check(minAc,"minAc"); check(minBond,"minBond");
        check(minKulc,"minKulc"); check(minUob,"minUob");
    }
    private static void check(double x,String n) {
        if (!Double.isFinite(x) || x<0 || x>1) throw new IllegalArgumentException(n+" in [0,1]");
    }
    public double correlation(MiningMode m) {
        return switch(m) {
            case CORHUOPM_AC -> minAc;
            case CORHUOPM_BOND -> minBond;
            case COUPM_KULC_ADAPTED -> minKulc;
            case UOB_POSTFILTER,UOB_FULL,UOB_NO_EDA,UOB_NO_UO_BOUND,UOB_EAGER_DUO,UOB_RECOMPUTE_DUO -> minUob;
            default -> 0.0;
        };
    }
}