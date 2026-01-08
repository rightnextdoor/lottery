package com.lotteryapp.lottery.ingestion.model;

import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngestedGameList {

    private String stateCode;

    private Long sourceId;
    private Instant fetchedAt;
    private Map<String, Object> meta;

    private List<GameInfo> games;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GameInfo {
        private String gameKey;
        private String displayName;
    }
}
