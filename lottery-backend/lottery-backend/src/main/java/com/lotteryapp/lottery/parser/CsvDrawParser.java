package com.lotteryapp.lottery.parser;

import com.lotteryapp.common.exception.BadRequestException;
import com.lotteryapp.lottery.domain.source.SourceType;
import com.lotteryapp.lottery.ingestion.model.IngestedDraw;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class CsvDrawParser implements DrawParser {

    private static final Pattern INT = Pattern.compile("\\b\\d{1,2}\\b");

    private static final Pattern MONEY = Pattern.compile("\\$?\\s*([0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)");


    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("M/d/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("M-d-yyyy"),
            DateTimeFormatter.ofPattern("MM-d-yyyy")
    );

    @Override
    public SourceType supportedSourceType() {
        return SourceType.CSV;
    }

    @Override
    public boolean supports(String parserKey) {
        // generic
        return true;
    }

    @Override
    public List<IngestedDraw> parse(byte[] bytes, Long gameModeId, String stateCode) {
        if (bytes == null || bytes.length == 0) {
            throw new BadRequestException("CSV is empty");
        }

        String text = new String(bytes, StandardCharsets.UTF_8);
        List<String> lines = Arrays.stream(text.split("\\R"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();

        if (lines.isEmpty()) throw new BadRequestException("CSV has no lines");

        List<String> header = parseCsvRow(lines.get(0));
        Map<String, Integer> idx = indexHeader(header);

        Integer dateCol = first(idx, "draw date", "draw_date", "date");
        Integer winningCol = first(idx, "winning numbers", "winning_numbers", "winningnumbers", "numbers");
        Integer multCol  = first(idx, "multiplier", "power play", "power_play", "megaplier");

        // optional metadata columns (best-effort)
        Integer jackpotCol = first(idx, "jackpot", "jackpot_amount", "jackpot amount", "estimated jackpot", "estimated_jackpot");
        Integer cashCol = first(idx, "cash value", "cash_value", "cash", "estimated cash value", "estimated_cash_value");
        Integer timeCol = first(idx, "draw time", "draw_time", "time");
        Integer tzCol = first(idx, "time zone", "timezone", "time_zone", "timeZoneId", "time_zone_id");

        if (dateCol == null || winningCol == null) {
            throw new BadRequestException("CSV header missing required columns (draw date / winning numbers)");
        }

        List<IngestedDraw> out = new ArrayList<>();

        for (int i = 1; i < lines.size(); i++) {
            List<String> row = parseCsvRow(lines.get(i));
            if (row.isEmpty()) continue;

            LocalDate date = parseDate(cell(row, dateCol));
            if (date == null) continue;

            String winning = cell(row, winningCol);
            if (winning == null || winning.isBlank()) continue;

            List<Integer> nums = extractInts(winning);
            if (nums.size() < 5) continue;

            // Heuristic: first 5 are white, last is red (Powerball/Mega Ball).
            // Some games have different counts; the service layer should validate against Rules.
            List<Integer> whites = new ArrayList<>(nums.subList(0, Math.min(5, nums.size())));
            List<Integer> reds = new ArrayList<>();
            if (nums.size() > 5) {
                reds.add(nums.get(nums.size() - 1));
            }

            Integer mult = parseInt(cell(row, multCol));

            Long jackpot = parseMoney(cell(row, jackpotCol));
            Long cash = parseMoney(cell(row, cashCol));
            LocalTime drawTime = parseTime(cell(row, timeCol));
            String tz = normText(cell(row, tzCol));

            out.add(IngestedDraw.builder()
                    .drawDate(date)
                    .whiteNumbers(whites)
                    .redNumbers(reds)
                    .multiplier(mult)
                    .jackpotAmount(jackpot)
                    .cashValue(cash)
                    .drawTimeLocal(drawTime)
                    .drawTimeZoneId(tz)
                    .build());
        }

        if (out.isEmpty()) {
            throw new BadRequestException("CSV parsed but no draws were recognized");
        }

        return out;
    }

    private static Map<String, Integer> indexHeader(List<String> header) {
        Map<String, Integer> out = new HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            out.put(norm(header.get(i)), i);
        }
        return out;
    }

    private static String norm(String s) {
        if (s == null) return "";
        return s.trim().toLowerCase(Locale.ROOT).replace("_", " ");
    }

    private static Integer first(Map<String, Integer> idx, String... keys) {
        for (String k : keys) {
            Integer v = idx.get(norm(k));
            if (v != null) return v;
        }
        return null;
    }

    private static String cell(List<String> row, Integer col) {
        if (col == null || col < 0 || col >= row.size()) return null;
        String v = row.get(col);
        return v == null ? null : v.trim();
    }

    private static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim();
        for (DateTimeFormatter f : DATE_FORMATS) {
            try {
                return LocalDate.parse(t, f);
            } catch (Exception ignore) {}
        }
        return null;
    }

    private static Integer parseInt(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception ignore) {
            return null;
        }
    }

    private static Long parseMoney(String s) {
        if (s == null || s.isBlank()) return null;
        Matcher m = MONEY.matcher(s);
        if (!m.find()) return null;
        try {
            return Long.parseLong(m.group(1).replace(",", ""));
        } catch (Exception e) {
            return null;
        }
    }

    private static LocalTime parseTime(String s) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim();

        // ISO HH:mm[:ss]
        try {
            if (t.length() >= 5 && t.charAt(2) == ':') {
                return LocalTime.parse(t.length() > 8 ? t.substring(0, 8) : t);
            }
        } catch (Exception ignore) {}

        // 12h
        Matcher m = Pattern.compile("\b(1[0-2]|0?[1-9]):([0-5]\\d)\\s*(AM|PM)\b", Pattern.CASE_INSENSITIVE).matcher(t);
        if (m.find()) {
            int hh = Integer.parseInt(m.group(1));
            int mm = Integer.parseInt(m.group(2));
            String ap = m.group(3).toUpperCase(Locale.ROOT);
            if ("PM".equals(ap) && hh != 12) hh += 12;
            if ("AM".equals(ap) && hh == 12) hh = 0;
            return LocalTime.of(hh, mm);
        }

        return null;
    }

    private static String normText(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isBlank() ? null : t;
    }

    private static List<Integer> extractInts(String s) {
        List<Integer> out = new ArrayList<>();
        if (s == null) return out;
        Matcher m = INT.matcher(s);
        while (m.find()) {
            try { out.add(Integer.parseInt(m.group(0))); } catch (Exception ignore) {}
        }
        return out;
    }

    // Minimal CSV row parser that respects quotes
    private static List<String> parseCsvRow(String line) {
        if (line == null) return List.of();
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    // escaped quote
                    cur.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
                continue;
            }

            if (c == ',' && !inQuotes) {
                out.add(cur.toString());
                cur.setLength(0);
                continue;
            }

            cur.append(c);
        }

        out.add(cur.toString());
        return out.stream().map(String::trim).toList();
    }
}
