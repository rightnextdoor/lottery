package com.lotteryapp.lottery.dto.ticketgen.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record GenerateBatchRequest(
        @NotNull Long gameModeId,
        @NotEmpty @Valid List<TicketSpecRequest> ticketSpecs
) {}
