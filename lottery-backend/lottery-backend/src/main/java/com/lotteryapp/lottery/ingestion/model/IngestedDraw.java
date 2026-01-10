package com.lotteryapp.lottery.ingestion.model;

import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngestedDraw {

    private Long gameModeId;
    private String stateCode;

    private Long sourceId;
    private Instant fetchedAt;
    private Map<String, Object> meta;

    private LocalDate drawDate;

    private List<Integer> whiteNumbers;
    private List<Integer> redNumbers;

    private Integer multiplier;

    private Long jackpotAmount;
    private Long cashValue;
    private LocalTime drawTimeLocal;
    private String drawTimeZoneId;
}
