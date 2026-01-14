package com.lotteryapp.lottery.dto.generatorspec.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GeneratorSpecResponse {

    private Long id;
    private Long gameModeId;

    private int ticketCount;

    private Long whiteGroupId;
    private Long redGroupId;

    private boolean excludeLastDrawNumbers;
}
