package com.lotteryapp.lottery.parser;

import com.lotteryapp.lottery.domain.source.SourceType;
import com.lotteryapp.lottery.ingestion.model.IngestedRules;

public interface RulesParser {
    SourceType supportedSourceType();
    boolean supports(String parserKey);
    IngestedRules parse(byte[] bytes, Long gameModeId, String stateCode);
}
