package com.lotteryapp.lottery.dto.batch.request;

import jakarta.validation.constraints.NotNull;

public record UpdateKeepForeverRequest(
        @NotNull Long batchId,
        @NotNull Boolean keepForever
) {}
