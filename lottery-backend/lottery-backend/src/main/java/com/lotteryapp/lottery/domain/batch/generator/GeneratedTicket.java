package com.lotteryapp.lottery.domain.batch.generator;

import java.util.List;

public record GeneratedTicket(
        int ticketIndex,
        List<Integer> whiteNumbers,
        List<Integer> redNumbers,
        String whiteGroupKey,
        String redGroupKey
) {}
