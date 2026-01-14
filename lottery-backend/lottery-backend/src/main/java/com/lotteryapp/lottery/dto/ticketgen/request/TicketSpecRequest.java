package com.lotteryapp.lottery.dto.ticketgen.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

public record TicketSpecRequest(
        @Min(1) int ticketCount,
        @Positive Long whiteGroupId,
        @Positive Long redGroupId,
        boolean excludeLastDrawNumbers
) {}
