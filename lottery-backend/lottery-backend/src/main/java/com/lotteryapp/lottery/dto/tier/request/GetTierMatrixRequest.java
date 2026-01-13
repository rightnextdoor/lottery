package com.lotteryapp.lottery.dto.tier.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetTierMatrixRequest {
    private Long gameModeId;
}