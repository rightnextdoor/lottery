package com.lotteryapp.lottery.domain.batch.generator;

import com.lotteryapp.lottery.domain.gamemode.Rules;
import com.lotteryapp.lottery.domain.group.TicketGroup;
import com.lotteryapp.lottery.domain.numbers.NumberBall;
import com.lotteryapp.lottery.domain.numbers.PoolType;

import java.util.List;
import java.util.Set;

public record GeneratorRequest(
        GeneratorMode mode,
        int ticketCount,
        Rules rules,

        TicketGroup whiteGroup,
        TicketGroup redGroup,

        Set<Integer> excludedWhiteNumbers,
        Set<Integer> excludedRedNumbers,

        List<NumberBall> whiteBalls,
        List<NumberBall> redBalls,

        GeneratorOptions options
) {
    public GeneratorRequest {
        if (ticketCount <= 0) throw new IllegalArgumentException("ticketCount must be > 0");
        if (rules == null) throw new IllegalArgumentException("rules is required");
        if (options == null) options = GeneratorOptions.defaults();
        if (excludedWhiteNumbers == null) excludedWhiteNumbers = Set.of();
        if (excludedRedNumbers == null) excludedRedNumbers = Set.of();

        if (mode == GeneratorMode.GROUP_WEIGHTED) {
            if (whiteBalls == null) throw new IllegalArgumentException("whiteBalls required for GROUP_WEIGHTED");
            if (rules.getRedPickCount() != null && rules.getRedPickCount() > 0 && redBalls == null) {
                throw new IllegalArgumentException("redBalls required for GROUP_WEIGHTED when redPickCount > 0");
            }
        }
    }

    public Set<Integer> excludedFor(PoolType poolType) {
        return poolType == PoolType.WHITE ? excludedWhiteNumbers : excludedRedNumbers;
    }

    public List<NumberBall> ballsFor(PoolType poolType) {
        return poolType == PoolType.WHITE ? whiteBalls : redBalls;
    }

    public TicketGroup groupFor(PoolType poolType) {
        return poolType == PoolType.WHITE ? whiteGroup : redGroup;
    }
}
