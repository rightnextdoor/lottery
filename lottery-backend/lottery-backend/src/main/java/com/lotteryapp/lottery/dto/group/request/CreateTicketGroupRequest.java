package com.lotteryapp.lottery.dto.group.request;

import com.lotteryapp.lottery.domain.group.GroupMode;
import com.lotteryapp.lottery.domain.numbers.PoolType;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateTicketGroupRequest {

    @NotNull
    private Long gameModeId;

    @NotNull
    private PoolType poolType;

    @NotNull
    private GroupMode groupMode;

    private Integer hotCount;
    private Integer midCount;
    private Integer coldCount;

    private Integer hotPct;
    private Integer midPct;
    private Integer coldPct;
}
