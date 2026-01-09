package com.lotteryapp.lottery.dto.rules.response;

import lombok.*;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RulesResponse {

    private Long id;
    private Long gameModeId;

    private LocalDate formatStartDate;

    private Integer whiteMin;
    private Integer whiteMax;
    private Integer whitePickCount;
    private Boolean whiteOrdered;
    private Boolean whiteAllowRepeats;

    private Integer redMin;
    private Integer redMax;
    private Integer redPickCount;
    private Boolean redOrdered;
    private Boolean redAllowRepeats;
}
