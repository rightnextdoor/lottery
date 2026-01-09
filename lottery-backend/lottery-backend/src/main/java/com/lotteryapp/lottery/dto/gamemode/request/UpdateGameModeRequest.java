package com.lotteryapp.lottery.dto.gamemode.request;

import com.lotteryapp.lottery.domain.gamemode.DrawDay;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateGameModeRequest {

    @NotNull
    private Long id;

    private String displayName;

    private Set<DrawDay> drawDays;
}
