package com.lotteryapp.lottery.dto.batch.response;

import java.time.Instant;
import java.util.List;

public record SavedBatchResponse(
        Long id,
        Long gameModeId,
        String name,
        Instant createdAt,
        Boolean keepForever,
        Instant expiresAt,
        Boolean checked,
        String status,
        List<TicketResponse> tickets,
        List<BatchCheckRecordResponse> checkRecords
) {}
