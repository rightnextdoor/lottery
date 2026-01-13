package com.lotteryapp.lottery.dto.numberball.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetNumberBallsResponse {
    private List<NumberBallResponse> whiteBalls;
    private List<NumberBallResponse> redBalls;
}
