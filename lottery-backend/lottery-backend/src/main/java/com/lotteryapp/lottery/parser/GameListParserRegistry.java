package com.lotteryapp.lottery.parser;

import com.lotteryapp.common.exception.BadRequestException;
import com.lotteryapp.lottery.domain.source.SourceType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GameListParserRegistry {

    private final List<GameListParser> parsers;

    public GameListParserRegistry(List<GameListParser> parsers) {
        this.parsers = parsers;
    }

    public GameListParser resolve(SourceType sourceType, String parserKey) {
        for (GameListParser p : parsers) {
            if (p.supportedSourceType() == sourceType && p.supports(parserKey)) return p;
        }
        for (GameListParser p : parsers) {
            if (p.supportedSourceType() == sourceType && p.supports(null)) return p;
        }
        throw new BadRequestException("No game list parser registered for sourceType=" + sourceType + " parserKey=" + parserKey);
    }
}
