package com.lotteryapp.lottery.dto.draw.response;

import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DrawConflictResponse {
    private Long id;
    private Long drawResultId;
    private Long gameModeId;
    private LocalDate drawDate;

    private List<Integer> manualWhite;
    private List<Integer> manualRed;

    private List<Integer> officialWhite;
    private List<Integer> officialRed;

    private boolean acknowledged;
}
