package com.lotteryapp.lottery.dto.draw.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class GetDrawByDateRequest {
    @NotNull private Long gameModeId;
    @NotNull private String stateCode;
    @NotNull private LocalDate drawDate;
}
