package com.lotteryapp.lottery.parser;

import com.lotteryapp.common.exception.BadRequestException;
import com.lotteryapp.lottery.domain.source.SourceType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ScheduleParserRegistry {

    private final List<ScheduleParser> parsers;

    public ScheduleParserRegistry(List<ScheduleParser> parsers) {
        this.parsers = parsers;
    }

    public ScheduleParser resolve(SourceType sourceType, String parserKey) {
        for (ScheduleParser p : parsers) {
            if (p.supportedSourceType() == sourceType && p.supports(parserKey)) return p;
        }
        for (ScheduleParser p : parsers) {
            if (p.supportedSourceType() == sourceType && p.supports(null)) return p;
        }
        throw new BadRequestException("No schedule parser registered for sourceType=" + sourceType + " parserKey=" + parserKey);
    }
}
