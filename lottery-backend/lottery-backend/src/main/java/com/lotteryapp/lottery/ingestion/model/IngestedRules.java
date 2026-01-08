package com.lotteryapp.lottery.ingestion.model;

import lombok.*;

import java.time.Instant;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngestedRules {

    private Long gameModeId;
    private String stateCode;

    private Long sourceId;
    private Instant fetchedAt;
    private Map<String, Object> meta;

    private Integer whitePickCount;
    private Integer whiteMin;
    private Integer whiteMax;

    private Integer redPickCount;
    private Integer redMin;
    private Integer redMax;
}
