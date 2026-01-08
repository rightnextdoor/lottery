package com.lotteryapp.lottery.domain.numbers.tier;

import java.time.LocalDate;

public record TierWindow(LocalDate start, LocalDate end) {

    public TierWindow {
        if (start == null || end == null) {
            throw new IllegalArgumentException("TierWindow start/end cannot be null.");
        }
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("TierWindow start cannot be after end.");
        }
    }
}
