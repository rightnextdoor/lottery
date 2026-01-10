package com.lotteryapp.lottery.dto.draw.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UpsertDrawRequest {

    @NotNull private Long gameModeId;
    @NotNull private String stateCode;

    private LocalDate drawDate;

    private List<Integer> whiteNumbers;
    private List<Integer> redNumbers;

    private BigDecimal jackpotAmount;
    private BigDecimal cashValue;
    private LocalTime drawTimeLocal;
    private String drawTimeZoneId;
}
