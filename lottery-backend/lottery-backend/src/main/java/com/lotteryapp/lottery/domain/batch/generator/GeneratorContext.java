package com.lotteryapp.lottery.domain.batch.generator;

import com.lotteryapp.lottery.domain.gamemode.Rules;
import com.lotteryapp.lottery.domain.numbers.NumberBall;

import java.util.List;

public record GeneratorContext(
        Rules rules,
        List<NumberBall> whiteBalls,
        List<NumberBall> redBalls,
        GeneratorOptions options
) {
    public GeneratorContext {
        if (rules == null) throw new IllegalArgumentException("rules is required");
        if (options == null) options = GeneratorOptions.defaults();
    }
}
