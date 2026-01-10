package com.lotteryapp.lottery.dto.draw.response;

import com.lotteryapp.lottery.domain.draw.DrawOrigin;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DrawResponse {
    private Long id;
    private LocalDate drawDate;
    private DrawOrigin origin;

    private List<Integer> whiteNumbers;
    private List<Integer> redNumbers;

    private boolean hasConflict;
    private Long conflictId;
    private boolean conflictAcknowledged;
}
