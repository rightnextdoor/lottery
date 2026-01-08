package com.lotteryapp.lottery.parser;

import com.lotteryapp.lottery.domain.source.SourceType;
import com.lotteryapp.lottery.ingestion.model.IngestedGameList;

public interface GameListParser {
    SourceType supportedSourceType();
    boolean supports(String parserKey);
    IngestedGameList parse(byte[] bytes, String stateCode);
}
