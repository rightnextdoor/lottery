package com.lotteryapp.lottery.dto.group.response;

import com.lotteryapp.lottery.domain.group.GroupMode;
import com.lotteryapp.lottery.domain.numbers.PoolType;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketGroupResponse {

    private Long id;

    private Long gameModeId;

    private PoolType poolType;

    private GroupMode groupMode;

    private String groupKey;

    private String displayName;

    private Integer hotCount;
    private Integer midCount;
    private Integer coldCount;

    private Integer hotPct;
    private Integer midPct;
    private Integer coldPct;

    private Instant createdAt;
    private Instant updatedAt;
}