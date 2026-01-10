package com.lotteryapp.lottery.dto.draw.response;

import com.lotteryapp.lottery.dto.gamemode.response.GameModeResponse;
import lombok.*;

import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DrawBundleResponse {
    private GameModeResponse gameMode;
    private DrawResponse draw;
    private List<DrawResponse> draws;
}
