package com.lotteryapp.lottery.dto.rules.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import lombok.*;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateRulesRequest {

    @NotNull
    private Long gameModeId;

    private LocalDate formatStartDate;

    @Min(1)
    private Integer whiteMin;

    @Min(1)
    private Integer whiteMax;

    @Min(1)
    private Integer whitePickCount;

    private Boolean whiteOrdered;
    private Boolean whiteAllowRepeats;

    @Min(0)
    private Integer redMin;

    @Min(0)
    private Integer redMax;

    @Min(0)
    private Integer redPickCount;

    private Boolean redOrdered;
    private Boolean redAllowRepeats;
}
