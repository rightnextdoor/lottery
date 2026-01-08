package com.lotteryapp.lottery.dto.source.request;

import com.lotteryapp.lottery.domain.source.SourceType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSourceRequest {

    @NotBlank
    private String stateCode;

    @NotNull
    private Long gameModeId;

    @Min(0)
    private int priority;

    @NotNull
    private Boolean enabled;

    @NotNull
    private SourceType sourceType;

    @NotBlank
    private String parserKey;

    @NotBlank
    private String urlTemplate;

    // capabilities
    @NotNull
    private Boolean supportsGameList;

    @NotNull
    private Boolean drawLatest;

    @NotNull
    private Boolean drawByDate;

    @NotNull
    private Boolean drawHistory;

    @NotNull
    private Boolean supportsRules;

    @NotNull
    private Boolean supportsSchedule;
}
