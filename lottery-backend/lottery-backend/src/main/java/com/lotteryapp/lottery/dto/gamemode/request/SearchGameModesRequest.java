package com.lotteryapp.lottery.dto.gamemode.request;

import com.lotteryapp.lottery.domain.gamemode.GameScope;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchGameModesRequest {

    private String query;

    private GameScope scope;

    private String stateCode;

    @Builder.Default
    private int page = 0;

    @Builder.Default
    private int size = 25;
}
