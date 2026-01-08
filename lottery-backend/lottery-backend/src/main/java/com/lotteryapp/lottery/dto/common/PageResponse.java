package com.lotteryapp.lottery.dto.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PageResponse<T> {

    private String message;

    private List<T> items;

    private PageMeta meta;

    private List<String> warnings;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PageMeta {
        private int pageNumber;
        private int pageSize;

        private long totalElements;
        private int totalPages;

        private boolean hasNext;
        private boolean hasPrevious;

        private String sort;
    }
}
