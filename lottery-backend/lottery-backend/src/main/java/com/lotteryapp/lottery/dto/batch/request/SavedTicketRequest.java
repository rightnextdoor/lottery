package com.lotteryapp.lottery.dto.batch.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SavedTicketRequest(
        @Min(1) int ticketNumber,
        @NotNull @Valid SavedPicksRequest picks
) {}
