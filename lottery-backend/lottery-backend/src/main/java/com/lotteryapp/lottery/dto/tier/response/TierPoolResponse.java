package com.lotteryapp.lottery.dto.tier.response;

import com.lotteryapp.lottery.dto.numberball.response.NumberBallResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TierPoolResponse {
    private List<NumberBallResponse> hot;
    private List<NumberBallResponse> mid;
    private List<NumberBallResponse> cold;
}
