package com.lotteryapp.lottery.parser;

import com.lotteryapp.common.exception.BadRequestException;
import com.lotteryapp.lottery.domain.source.SourceType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RulesParserRegistry {

    private final List<RulesParser> parsers;

    public RulesParserRegistry(List<RulesParser> parsers) {
        this.parsers = parsers;
    }

    public RulesParser resolve(SourceType sourceType, String parserKey) {
        for (RulesParser p : parsers) {
            if (p.supportedSourceType() == sourceType && p.supports(parserKey)) return p;
        }
        for (RulesParser p : parsers) {
            if (p.supportedSourceType() == sourceType && p.supports(null)) return p;
        }
        throw new BadRequestException("No rules parser registered for sourceType=" + sourceType + " parserKey=" + parserKey);
    }
}
