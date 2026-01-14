package com.lotteryapp.lottery.domain.batch.generator;

public record GeneratedSpecTicket(
        int ticketNumber,
        GeneratedPicks picks
) {}
