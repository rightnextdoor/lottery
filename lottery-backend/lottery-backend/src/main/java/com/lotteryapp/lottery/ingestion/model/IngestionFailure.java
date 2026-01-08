package com.lotteryapp.lottery.ingestion.model;

import lombok.*;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngestionFailure {

    private String message;

    private String reasonCode;

    private Map<String, Object> details;

    private List<Attempt> attempts;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Attempt {
        private Long sourceId;
        private Integer priority;
        private String sourceType;
        private String parserKey;

        private String url;
        private String finalUrl;

        private Integer statusCode;
        private String contentType;

        private String errorMessage;
    }
}
