package com.lotteryapp.lottery.domain.batch.generator;

import java.util.List;

public record GeneratedPicks(
        List<Integer> white,
        List<Integer> red
) {}
