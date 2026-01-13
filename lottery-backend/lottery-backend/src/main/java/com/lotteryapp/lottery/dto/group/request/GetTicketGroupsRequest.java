package com.lotteryapp.lottery.dto.group.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GetTicketGroupsRequest {

    @NotNull
    private Long gameModeId;
}
