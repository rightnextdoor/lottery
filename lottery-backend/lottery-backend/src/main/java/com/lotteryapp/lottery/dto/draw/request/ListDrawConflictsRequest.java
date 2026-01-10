package com.lotteryapp.lottery.dto.draw.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ListDrawConflictsRequest {
    @NotNull private Long gameModeId;

    private Integer pageNumber;
    private Integer pageSize;
    private String sort;
}
