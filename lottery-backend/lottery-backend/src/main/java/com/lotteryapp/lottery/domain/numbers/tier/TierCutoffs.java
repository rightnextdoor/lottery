package com.lotteryapp.lottery.domain.numbers.tier;

public record TierCutoffs(int hotPct, int midPct) {

    public TierCutoffs {
        if (hotPct < 0 || hotPct > 100) throw new IllegalArgumentException("hotPct must be 0..100");
        if (midPct < 0 || midPct > 100) throw new IllegalArgumentException("midPct must be 0..100");
        if (hotPct + midPct > 100) throw new IllegalArgumentException("hotPct + midPct must be <= 100");
    }

    public int coldPct() {
        return 100 - hotPct - midPct;
    }


    public static TierCutoffs defaultCutoffs() {
        return new TierCutoffs(20, 50);
    }
}
