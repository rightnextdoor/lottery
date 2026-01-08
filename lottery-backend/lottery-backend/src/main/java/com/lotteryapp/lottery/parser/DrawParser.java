package com.lotteryapp.lottery.parser;

import com.lotteryapp.lottery.domain.source.SourceType;
import com.lotteryapp.lottery.ingestion.model.IngestedDraw;

import java.util.List;

public interface DrawParser {
    SourceType supportedSourceType();

    boolean supports(String parserKey);

    List<IngestedDraw> parse(byte[] bytes, Long gameModeId, String stateCode);
}
