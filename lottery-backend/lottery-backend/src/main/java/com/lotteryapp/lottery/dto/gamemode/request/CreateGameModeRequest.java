package com.lotteryapp.lottery.dto.gamemode.request;

import com.lotteryapp.lottery.domain.gamemode.DrawDay;
import com.lotteryapp.lottery.domain.gamemode.GameScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateGameModeRequest {

    @NotBlank
    private String displayName;

    @NotNull
    private GameScope scope;

    private String jurisdictionCode;

    @NotNull
    private Set<DrawDay> drawDays;
}
