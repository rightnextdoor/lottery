package com.lotteryapp.lottery.dto.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private String message;
    private T data;
    private List<String> warnings;
    private Map<String, Object> meta;

    public static <T> ApiResponse<T> ok(String message, T data) {
        return ApiResponse.<T>builder()
                .message(message)
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> ok(String message, T data, List<String> warnings, Map<String, Object> meta) {
        return ApiResponse.<T>builder()
                .message(message)
                .data(data)
                .warnings(warnings)
                .meta(meta)
                .build();
    }
}
