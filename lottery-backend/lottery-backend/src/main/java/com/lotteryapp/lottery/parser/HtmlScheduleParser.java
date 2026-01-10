package com.lotteryapp.lottery.parser;

import com.lotteryapp.common.exception.BadRequestException;
import com.lotteryapp.lottery.domain.source.SourceType;
import com.lotteryapp.lottery.ingestion.model.IngestedSchedule;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class HtmlScheduleParser implements ScheduleParser {

    private static final List<String> DAYS = List.of(
            "MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY","SUNDAY"
    );

    // e.g. "10:59 PM", "8:00pm", "23:00"
    private static final Pattern TIME_12H = Pattern.compile("\b(1[0-2]|0?[1-9]):([0-5]\\d)\s*(AM|PM)\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern TIME_24H = Pattern.compile("\b([01]?\\d|2[0-3]):([0-5]\\d)\b");
    // very lightweight TZ detection; prefer explicit IANA if present
    private static final Pattern IANA_TZ = Pattern.compile("\b[A-Za-z]+/[A-Za-z_]+\b");
    private static final Pattern TZ_ABBR = Pattern.compile("\b(ET|EST|EDT|CT|CST|CDT|MT|MST|MDT|PT|PST|PDT)\b");

    @Override
    public SourceType supportedSourceType() {
        return SourceType.HTML;
    }

    @Override
    public boolean supports(String parserKey) {
        return true;
    }

    @Override
    public IngestedSchedule parse(byte[] bytes, Long gameModeId, String stateCode) {
        if (bytes == null || bytes.length == 0) {
            throw new BadRequestException("Schedule HTML is empty");
        }

        String text = new String(bytes, StandardCharsets.UTF_8)
                .replaceAll("<[^>]*>", " ");
        String upper = text.toUpperCase(Locale.ROOT);

        // days
        Set<String> found = new LinkedHashSet<>();
        for (String d : DAYS) {
            if (upper.contains(d)) found.add(d);
        }

        // time + timezone (best-effort)
        LocalTime drawTime = parseTime(text);
        String tz = parseTimeZoneId(text);

        return IngestedSchedule.builder()
                .gameModeId(gameModeId)
                .stateCode(stateCode)
                .drawDays(new ArrayList<>(found))
                .drawTimeLocal(drawTime)
                .drawTimeZoneId(tz)
                .build();
    }

    private static LocalTime parseTime(String s) {
        if (s == null) return null;

        Matcher m12 = TIME_12H.matcher(s);
        if (m12.find()) {
            int hh = Integer.parseInt(m12.group(1));
            int mm = Integer.parseInt(m12.group(2));
            String ap = m12.group(3).toUpperCase(Locale.ROOT);
            if ("PM".equals(ap) && hh != 12) hh += 12;
            if ("AM".equals(ap) && hh == 12) hh = 0;
            return LocalTime.of(hh, mm);
        }

        Matcher m24 = TIME_24H.matcher(s);
        if (m24.find()) {
            int hh = Integer.parseInt(m24.group(1));
            int mm = Integer.parseInt(m24.group(2));
            return LocalTime.of(hh, mm);
        }

        return null;
    }

    private static String parseTimeZoneId(String s) {
        if (s == null) return null;

        Matcher mi = IANA_TZ.matcher(s);
        if (mi.find()) return mi.group(0);

        Matcher ma = TZ_ABBR.matcher(s.toUpperCase(Locale.ROOT));
        if (ma.find()) {
            // keep abbreviations as-is; upstream can map to IANA if desired
            return ma.group(1);
        }

        return null;
    }
}
