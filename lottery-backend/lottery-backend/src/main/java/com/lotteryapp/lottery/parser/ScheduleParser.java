package com.lotteryapp.lottery.parser;

import com.lotteryapp.lottery.domain.source.SourceType;
import com.lotteryapp.lottery.ingestion.model.IngestedSchedule;

public interface ScheduleParser {
    SourceType supportedSourceType();
    boolean supports(String parserKey);
    IngestedSchedule parse(byte[] bytes, Long gameModeId, String stateCode);
}
