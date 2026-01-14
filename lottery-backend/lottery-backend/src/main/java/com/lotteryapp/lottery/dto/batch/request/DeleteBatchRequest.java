package com.lotteryapp.lottery.dto.batch.request;

import jakarta.validation.constraints.NotNull;

public record DeleteBatchRequest(
        @NotNull Long batchId
) {}
