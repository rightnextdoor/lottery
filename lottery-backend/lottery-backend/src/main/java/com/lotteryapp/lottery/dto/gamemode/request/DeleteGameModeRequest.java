package com.lotteryapp.lottery.dto.gamemode.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeleteGameModeRequest {

    @NotNull
    private Long id;
}

