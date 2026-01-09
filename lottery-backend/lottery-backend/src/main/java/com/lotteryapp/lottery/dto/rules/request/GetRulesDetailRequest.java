package com.lotteryapp.lottery.dto.rules.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GetRulesDetailRequest {

    @NotNull
    private Long gameModeId;
}
