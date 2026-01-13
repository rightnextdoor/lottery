package com.lotteryapp.lottery.dto.group.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateTicketGroupRequest {

    @NotNull
    private Long groupId;

    private Integer hotCount;
    private Integer midCount;
    private Integer coldCount;

    private Integer hotPct;
    private Integer midPct;
    private Integer coldPct;
}
