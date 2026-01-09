package com.lotteryapp.lottery.parser;

import com.lotteryapp.common.exception.BadRequestException;
import com.lotteryapp.lottery.domain.source.SourceType;
import com.lotteryapp.lottery.ingestion.model.IngestedSchedule;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class HtmlScheduleParser implements ScheduleParser {

    private static final List<String> DAYS = List.of(
            "MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY","SUNDAY"
    );

    // e.g. "10:59 PM", "10 PM", "10:59 p.m."
    private static final Pattern TIME_12H = Pattern.compile(
            "\\b(\\d{1,2})(?::(\\d{2}))?\\s*(AM|PM|A\\.M\\.|P\\.M\\.)\\b",
            Pattern.CASE_INSENSITIVE
    );

    // e.g. "ET", "EST", "EDT", "CT", "CST", "CDT", "MT", "MST", "MDT", "PT", "PST", "PDT"
    private static final Pattern TZ_ABBR = Pattern.compile(
            "\\b(ET|EST|EDT|CT|CST|CDT|MT|MST|MDT|PT|PST|PDT)\\b",
            Pattern.CASE_INSENSITIVE
    );

    // Optional next draw date patterns
    private static final Pattern DATE_US = Pattern.compile("\\b(\\d{1,2})/(\\d{1,2})/(\\d{4})\\b");
    private static final Pattern DATE_ISO = Pattern.compile("\\b(\\d{4})-(\\d{2})-(\\d{2})\\b");

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
                .replaceAll("<[^>]*>", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();

        String upper = text.toUpperCase(Locale.ROOT);

        // 1) Draw days
        Set<String> foundDays = new LinkedHashSet<>();
        for (String d : DAYS) {
            if (upper.contains(d)) foundDays.add(d);
        }
        if (foundDays.isEmpty()) {
            throw new BadRequestException("Could not find draw days in HTML");
        }

        // 2) Draw time (best-effort)
        LocalTime drawTimeLocal = null;
        Matcher tm = TIME_12H.matcher(text);
        if (tm.find()) {
            int hour = Integer.parseInt(tm.group(1));
            int minute = tm.group(2) == null ? 0 : Integer.parseInt(tm.group(2));
            String ap = tm.group(3).toUpperCase(Locale.ROOT);

            boolean pm = ap.startsWith("P");
            boolean am = ap.startsWith("A");

            if (pm && hour < 12) hour += 12;
            if (am && hour == 12) hour = 0;

            if (hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59) {
                drawTimeLocal = LocalTime.of(hour, minute);
            }
        }

        // 3) Time zone (best-effort)
        String drawTimeZoneId = null;

        // Prefer explicit "EASTERN/CENTRAL/..." if present
        if (upper.contains("EASTERN")) drawTimeZoneId = "America/New_York";
        else if (upper.contains("CENTRAL")) drawTimeZoneId = "America/Chicago";
        else if (upper.contains("MOUNTAIN")) drawTimeZoneId = "America/Denver";
        else if (upper.contains("PACIFIC")) drawTimeZoneId = "America/Los_Angeles";
        else {
            Matcher tz = TZ_ABBR.matcher(text);
            if (tz.find()) {
                String abbr = tz.group(1).toUpperCase(Locale.ROOT);
                drawTimeZoneId = switch (abbr) {
                    case "ET", "EST", "EDT" -> "America/New_York";
                    case "CT", "CST", "CDT" -> "America/Chicago";
                    case "MT", "MST", "MDT" -> "America/Denver";
                    case "PT", "PST", "PDT" -> "America/Los_Angeles";
                    default -> null;
                };
            }
        }

        // 4) Next draw date (optional; harmless if null)
        LocalDate nextDrawDate = parseFirstDateNearKeyword(upper, text, "NEXT");
        if (nextDrawDate == null) {
            nextDrawDate = parseFirstDateNearKeyword(upper, text, "NEXT DRAW");
        }

        return IngestedSchedule.builder()
                .gameModeId(gameModeId)
                .stateCode(stateCode)
                .drawDays(new ArrayList<>(foundDays))
                .nextDrawDate(nextDrawDate)
                .drawTimeLocal(drawTimeLocal)
                .drawTimeZoneId(drawTimeZoneId)
                .build();
    }

    private static LocalDate parseFirstDateNearKeyword(String upper, String text, String keyword) {
        int idx = upper.indexOf(keyword);
        if (idx < 0) return null;

        int start = Math.max(0, idx - 50);
        int end = Math.min(text.length(), idx + 250);
        String window = text.substring(start, end);

        Matcher m1 = DATE_US.matcher(window);
        if (m1.find()) {
            int mm = Integer.parseInt(m1.group(1));
            int dd = Integer.parseInt(m1.group(2));
            int yy = Integer.parseInt(m1.group(3));
            return LocalDate.of(yy, mm, dd);
        }

        Matcher m2 = DATE_ISO.matcher(window);
        if (m2.find()) {
            int yy = Integer.parseInt(m2.group(1));
            int mm = Integer.parseInt(m2.group(2));
            int dd = Integer.parseInt(m2.group(3));
            return LocalDate.of(yy, mm, dd);
        }

        return null;
    }
}
