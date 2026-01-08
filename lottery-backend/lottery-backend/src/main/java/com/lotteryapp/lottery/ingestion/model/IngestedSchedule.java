package com.lotteryapp.lottery.ingestion.model;

import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngestedSchedule {

    private Long gameModeId;
    private String stateCode;

    private Long sourceId;
    private Instant fetchedAt;
    private Map<String, Object> meta;

    private List<String> drawDays;

    private LocalDate nextDrawDate;
}
