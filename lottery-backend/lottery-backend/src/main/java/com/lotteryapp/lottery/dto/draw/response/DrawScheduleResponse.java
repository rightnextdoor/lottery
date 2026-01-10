package com.lotteryapp.lottery.dto.draw.response;

import com.lotteryapp.lottery.domain.gamemode.DrawDay;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DrawScheduleResponse {
    private Set<DrawDay> drawDays;
    private LocalDate nextDrawDate;
    private LocalTime drawTimeLocal;
    private String drawTimeZoneId;
}
