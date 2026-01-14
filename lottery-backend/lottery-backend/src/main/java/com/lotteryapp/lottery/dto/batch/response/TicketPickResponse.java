package com.lotteryapp.lottery.dto.batch.response;

public record TicketPickResponse(
        String poolType,
        Integer position,
        Integer numberValue
) {}
