package com.lotteryapp.lottery.domain.batch.generator;

public record GeneratorOptions(
        double temperature,
        double alpha,
        Long randomSeed
) {
    public static GeneratorOptions defaults() {
        return new GeneratorOptions(1.75, 0.30, null);
    }
}
