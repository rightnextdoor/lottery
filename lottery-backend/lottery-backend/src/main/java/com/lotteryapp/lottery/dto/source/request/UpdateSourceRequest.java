package com.lotteryapp.lottery.dto.source.request;

import com.lotteryapp.lottery.domain.source.SourceType;
import jakarta.validation.constraints.Min;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSourceRequest {

    private String stateCode;
    private Long gameModeId;

    @Min(0)
    private Integer priority;

    private Boolean enabled;

    private SourceType sourceType;

    private String parserKey;
    private String urlTemplate;

    // capabilities
    private Boolean supportsGameList;
    private Boolean drawLatest;
    private Boolean drawByDate;
    private Boolean drawHistory;
    private Boolean supportsRules;
    private Boolean supportsSchedule;
    private Boolean supportsJackpotAmount;
    private Boolean supportsCashValue;
    private Boolean supportsDrawTime;
    private Boolean supportsTimeZone;
}
