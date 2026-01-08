package com.lotteryapp.lottery.parser;

import com.lotteryapp.common.exception.BadRequestException;
import com.lotteryapp.lottery.domain.source.SourceType;
import com.lotteryapp.lottery.ingestion.model.IngestedSchedule;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class HtmlScheduleParser implements ScheduleParser {

    private static final List<String> DAYS = List.of(
            "MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY","SUNDAY"
    );

    @Override
    public SourceType supportedSourceType() {
        return SourceType.HTML;
    }

    @Override
    public boolean supports(String parserKey) {
        if (parserKey == null) return true;
        String k = parserKey.trim().toUpperCase(Locale.ROOT);
        return k.contains("SCHED") || k.contains("HTML");
    }

    @Override
    public IngestedSchedule parse(byte[] bytes, Long gameModeId, String stateCode) {
        if (bytes == null || bytes.length == 0) {
            throw new BadRequestException("Schedule HTML is empty");
        }

        String text = new String(bytes, StandardCharsets.UTF_8)
                .replaceAll("<[^>]*>", " ");
        String upper = text.toUpperCase(Locale.ROOT);

        Set<String> found = new LinkedHashSet<>();
        for (String d : DAYS) {
            if (upper.contains(d)) found.add(d);
        }

        if (found.isEmpty()) {
            throw new BadRequestException("Could not find draw days in HTML");
        }

        return IngestedSchedule.builder()
                .gameModeId(gameModeId)
                .stateCode(stateCode)
                .drawDays(new ArrayList<>(found))
                .build();
    }
}
