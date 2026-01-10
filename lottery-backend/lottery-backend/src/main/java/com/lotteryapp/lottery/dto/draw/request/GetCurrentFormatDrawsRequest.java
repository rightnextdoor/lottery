package com.lotteryapp.lottery.dto.draw.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class GetCurrentFormatDrawsRequest {
    @NotNull private Long gameModeId;
    @NotNull private String stateCode;
}
