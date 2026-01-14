package com.lotteryapp.lottery.domain.batch.generator;

import com.lotteryapp.lottery.domain.group.TicketGroup;
import com.lotteryapp.lottery.domain.numbers.PoolType;

import java.util.Set;

public record GeneratorSpec(
        int ticketCount,
        TicketGroup whiteGroup,
        TicketGroup redGroup,
        boolean excludeLastDrawNumbers,
        Set<Integer> excludedWhiteNumbers,
        Set<Integer> excludedRedNumbers
) {
    public GeneratorSpec {
        if (ticketCount <= 0) throw new IllegalArgumentException("ticketCount must be > 0");
        if (excludedWhiteNumbers == null) excludedWhiteNumbers = Set.of();
        if (excludedRedNumbers == null) excludedRedNumbers = Set.of();
    }

    public Set<Integer> excludedFor(PoolType poolType) {
        return poolType == PoolType.WHITE ? excludedWhiteNumbers : excludedRedNumbers;
    }

    public TicketGroup groupFor(PoolType poolType) {
        return poolType == PoolType.WHITE ? whiteGroup : redGroup;
    }

    public Long whiteGroupId() {
        return whiteGroup == null ? null : whiteGroup.getId();
    }

    public Long redGroupId() {
        return redGroup == null ? null : redGroup.getId();
    }
}
