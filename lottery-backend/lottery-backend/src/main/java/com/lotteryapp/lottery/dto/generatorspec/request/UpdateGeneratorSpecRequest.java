package com.lotteryapp.lottery.dto.generatorspec.request;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateGeneratorSpecRequest {

    private Long gameModeId;
    private Long id;

    private int ticketCount;

    private Long whiteGroupId;
    private Long redGroupId;

    private boolean excludeLastDrawNumbers;
}
