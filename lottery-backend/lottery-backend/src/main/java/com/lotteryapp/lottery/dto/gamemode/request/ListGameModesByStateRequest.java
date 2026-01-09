package com.lotteryapp.lottery.dto.gamemode.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ListGameModesByStateRequest {

    @NotBlank
    private String stateCode;
}

