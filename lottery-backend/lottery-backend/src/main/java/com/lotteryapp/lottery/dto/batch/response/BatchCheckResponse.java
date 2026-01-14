package com.lotteryapp.lottery.dto.batch.response;

import java.time.LocalDate;
import java.util.List;

public record BatchCheckResponse(
        Long batchId,
        LocalDate drawDate,
        List<BatchCheckRecordResponse> specRecords
) {}
