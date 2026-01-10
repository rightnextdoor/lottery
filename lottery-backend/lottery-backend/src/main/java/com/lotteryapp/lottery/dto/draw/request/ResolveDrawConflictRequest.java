package com.lotteryapp.lottery.dto.draw.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ResolveDrawConflictRequest {
    @NotNull private Long conflictId;
    @NotNull private Resolution resolution;

    public enum Resolution {
        MANUAL,
        OFFICIAL
    }
}
