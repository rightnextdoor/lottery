package com.lotteryapp.lottery.dto.source.response;

import com.lotteryapp.lottery.domain.source.SourceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SourceResponse {

    private Long id;

    private String stateCode;
    private Long gameModeId;

    private int priority;
    private boolean enabled;

    private SourceType sourceType;
    private String parserKey;
    private String urlTemplate;

    // capabilities
    private boolean supportsGameList;
    private boolean drawLatest;
    private boolean drawByDate;
    private boolean drawHistory;
    private boolean supportsRules;
    private boolean supportsSchedule;

    private Instant createdAt;
    private Instant updatedAt;
}
