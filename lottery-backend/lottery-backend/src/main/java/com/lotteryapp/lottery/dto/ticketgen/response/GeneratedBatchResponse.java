package com.lotteryapp.lottery.dto.ticketgen.response;

import java.util.List;

public record GeneratedBatchResponse(
        List<GeneratedSpecResultResponse> specResults,
        List<String> warnings
) {}
