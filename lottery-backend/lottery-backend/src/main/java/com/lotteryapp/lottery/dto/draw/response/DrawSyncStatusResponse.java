package com.lotteryapp.lottery.dto.draw.response;

import com.lotteryapp.lottery.domain.gamemode.GameModeStatus;
import lombok.*;

import java.time.LocalDate;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DrawSyncStatusResponse {
    private GameModeStatus status;

    private LocalDate latestStoredDrawDate;
    private LocalDate latestExpectedDrawDate;

    private int missingDrawCountEstimate;
    private long conflictCount;
}
