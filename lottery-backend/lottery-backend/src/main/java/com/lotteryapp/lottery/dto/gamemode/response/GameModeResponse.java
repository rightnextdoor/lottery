package com.lotteryapp.lottery.dto.gamemode.response;

import com.lotteryapp.lottery.domain.gamemode.DrawDay;
import com.lotteryapp.lottery.domain.gamemode.GameModeStatus;
import com.lotteryapp.lottery.domain.gamemode.GameScope;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameModeResponse {

    private Long id;

    private String modeKey;

    private String displayName;

    private GameScope scope;

    private String jurisdictionCode;

    private Long rulesId;

    private LocalDate tierRangeStartDate;
    private LocalDate tierRangeEndDate;

    private Set<DrawDay> drawDays;

    private LocalDate nextDrawDate;

    private LocalTime drawTimeLocal;
    private String drawTimeZoneId;

    private LocalDate latestDrawDate;
    private String latestWhiteWinningCsv;
    private String latestRedWinningCsv;

    private BigDecimal latestJackpotAmount;
    private BigDecimal latestCashValue;

    private GameModeStatus status;
}
