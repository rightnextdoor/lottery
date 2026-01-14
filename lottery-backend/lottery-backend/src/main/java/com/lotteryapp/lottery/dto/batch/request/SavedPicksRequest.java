package com.lotteryapp.lottery.dto.batch.request;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SavedPicksRequest(
        @NotNull List<Integer> white,
        @NotNull List<Integer> red
) {}
