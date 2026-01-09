package com.lotteryapp.lottery.dto.gamemode.response;

import com.lotteryapp.lottery.dto.common.PageResponse;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchGameModesResponse {

    private PageResponse<GameModeResponse> results;
}
