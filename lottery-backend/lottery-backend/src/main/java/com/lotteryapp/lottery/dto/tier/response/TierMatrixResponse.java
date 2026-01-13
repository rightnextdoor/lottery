package com.lotteryapp.lottery.dto.tier.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TierMatrixResponse {
    private TierPoolResponse white;
    private TierPoolResponse red;
}
