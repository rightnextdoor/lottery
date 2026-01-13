package com.lotteryapp.lottery.dto.numberball.response;

import com.lotteryapp.lottery.domain.numbers.PoolType;
import com.lotteryapp.lottery.domain.numbers.StatusChange;
import com.lotteryapp.lottery.domain.numbers.Tier;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NumberBallResponse {
    // all fields from NumberBall entity
    private Long id;

    private Long gameModeId;
    private PoolType poolType;
    private Integer numberValue;

    private LocalDate lastDrawnDate;

    private Integer totalCount;
    private Integer tierCount;

    private Tier tier;
    private StatusChange statusChange;
}
