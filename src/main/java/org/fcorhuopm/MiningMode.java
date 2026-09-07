package org.fcorhuopm;

public enum MiningMode {
    HUOPM,
    CORHUOPM_AC,
    CORHUOPM_BOND,
    COUPM_KULC_ADAPTED,
    UOB_POSTFILTER,
    UOB_FULL,
    UOB_NO_EDA,
    UOB_NO_UO_BOUND,
    UOB_EAGER_DUO,
    UOB_RECOMPUTE_DUO;

    public boolean uobSemantics() {
        return this==UOB_POSTFILTER || this==UOB_FULL || this==UOB_NO_EDA
            || this==UOB_NO_UO_BOUND || this==UOB_EAGER_DUO || this==UOB_RECOMPUTE_DUO;
    }
}