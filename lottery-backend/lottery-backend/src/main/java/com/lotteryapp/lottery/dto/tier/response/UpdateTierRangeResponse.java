package com.lotteryapp.lottery.dto.tier.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTierRangeResponse {
    private Long gameModeId;
    private LocalDate tierRangeStartDate;
    private LocalDate tierRangeEndDate;
}