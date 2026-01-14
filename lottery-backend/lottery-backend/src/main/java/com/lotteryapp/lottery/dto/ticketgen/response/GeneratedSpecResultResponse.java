package com.lotteryapp.lottery.dto.ticketgen.response;

import java.util.List;

public record GeneratedSpecResultResponse(
        int specNumber,
        int ticketCount,
        Long whiteGroupId,
        Long redGroupId,
        boolean excludeLastDrawNumbers,
        List<GeneratedTicketResponse> tickets,
        List<String> warnings
) {}
