package com.lotteryapp.lottery.dto.batch.response;

import java.util.List;

public record TicketResponse(
        Long id,
        Integer specNumber,
        Integer ticketNumber,
        Boolean excludeLastDrawNumbers,
        Long whiteGroupId,
        Long redGroupId,
        List<TicketPickResponse> picks
) {}
