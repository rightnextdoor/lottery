package com.lotteryapp.lottery.dto.batch.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SaveBatchRequest(
        @NotNull Long gameModeId,
        Boolean keepForever,
        @NotEmpty @Valid List<SavedSpecResultRequest> specResults
) {}
