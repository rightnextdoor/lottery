package com.lotteryapp.lottery.dto.generatorspec.request;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateGeneratorSpecRequest {

    private Long gameModeId;

    private int ticketCount;

    private Long whiteGroupId; // nullable
    private Long redGroupId;   // nullable

    private boolean excludeLastDrawNumbers;
}
