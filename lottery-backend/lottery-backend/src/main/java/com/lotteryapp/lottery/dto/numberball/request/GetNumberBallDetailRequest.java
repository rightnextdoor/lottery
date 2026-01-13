package com.lotteryapp.lottery.dto.numberball.request;


import com.lotteryapp.lottery.domain.numbers.PoolType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetNumberBallDetailRequest {
    private Long gameModeId;
    private PoolType poolType;
    private Integer numberValue;
}
