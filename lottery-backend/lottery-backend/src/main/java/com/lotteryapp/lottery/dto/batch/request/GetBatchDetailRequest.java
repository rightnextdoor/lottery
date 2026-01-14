package com.lotteryapp.lottery.dto.batch.request;

import jakarta.validation.constraints.NotNull;

public record GetBatchDetailRequest(
        @NotNull Long batchId
) {}
