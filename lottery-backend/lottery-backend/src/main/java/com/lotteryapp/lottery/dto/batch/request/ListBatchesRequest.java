package com.lotteryapp.lottery.dto.batch.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ListBatchesRequest(
        @NotNull Long gameModeId,
        @Min(0) int page,
        @Min(1) int size
) {}
