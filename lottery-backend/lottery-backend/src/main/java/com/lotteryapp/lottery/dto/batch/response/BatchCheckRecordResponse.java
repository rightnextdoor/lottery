package com.lotteryapp.lottery.dto.batch.response;

import java.time.LocalDate;
import java.util.Map;

public record BatchCheckRecordResponse(
        Long id,
        LocalDate drawDate,
        Integer specNumber,
        Long whiteGroupId,
        Long redGroupId,
        Double pctAnyHit,
        Double pctRedHit,
        Map<Integer, Double> whiteHitPct
) {}
