package com.lotteryapp.lottery.parser;

import com.lotteryapp.common.exception.BadRequestException;
import com.lotteryapp.lottery.domain.source.SourceType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DrawParserRegistry {

    private final List<DrawParser> parsers;

    public DrawParserRegistry(List<DrawParser> parsers) {
        this.parsers = parsers;
    }

    public DrawParser resolve(SourceType sourceType, String parserKey) {
        // First: strict match (type + supports key)
        for (DrawParser p : parsers) {
            if (p.supportedSourceType() == sourceType && p.supports(parserKey)) {
                return p;
            }
        }

        // Second: fallback match (type only)
        for (DrawParser p : parsers) {
            if (p.supportedSourceType() == sourceType && p.supports(null)) {
                return p;
            }
        }

        throw new BadRequestException("No parser registered for sourceType=" + sourceType + " parserKey=" + parserKey);
    }
}
