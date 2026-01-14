package com.lotteryapp.lottery.dto.batch.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record SavedSpecResultRequest(
        Long whiteGroupId,
        Long redGroupId,
        boolean excludeLastDrawNumbers,
        @Min(1) int ticketCount,
        @NotEmpty @Valid List<SavedTicketRequest> tickets
) {}
