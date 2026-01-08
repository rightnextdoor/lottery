package com.lotteryapp.lottery.parser;

import com.lotteryapp.common.exception.BadRequestException;
import com.lotteryapp.lottery.domain.source.SourceType;
import com.lotteryapp.lottery.ingestion.model.IngestedDraw;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class CsvDrawParser implements DrawParser {

    private static final Pattern INT_PATTERN = Pattern.compile("\\d+");
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
        if (parserKey == null) return true;
        String k = parserKey.trim().toUpperCase(Locale.ROOT);
        return k.contains("CSV");
    }

    @Override
    public List<IngestedDraw> parse(byte[] bytes, Long gameModeId, String stateCode) {
        if (bytes == null || bytes.length == 0) {
            throw new BadRequestException("CSV response was empty");
        }

        String text = new String(bytes, StandardCharsets.UTF_8);
        List<String> lines = splitLines(text);
        if (lines.size() < 2) {
            throw new BadRequestException("CSV response has no data rows");
        }

        List<String> header = parseCsvRow(lines.get(0));
        Map<String, Integer> idx = indexHeader(header);

        Integer dateCol = first(idx, "draw date", "draw_date", "date");
        Integer winningCol = first(idx, "winning numbers", "winning_numbers", "winningnumbers", "numbers");
        Integer multCol  = first(idx, "multiplier", "power play", "power_play", "megaplier");

        if (dateCol == null || winningCol == null) {
            throw new BadRequestException("CSV header missing required columns (draw date / winning numbers)");
        }

        List<IngestedDraw> out = new ArrayList<>();

        for (int i = 1; i < lines.size(); i++) {
            List<String> row = parseCsvRow(lines.get(i));
            if (row.isEmpty()) continue;

            LocalDate drawDate = tryParseDate(cell(row, dateCol));
            if (drawDate == null) continue;

            List<Integer> nums = parseInts(cell(row, winningCol));
            if (nums.isEmpty()) continue;

            List<Integer> whites = nums;
            List<Integer> reds = null;

            // Common multi format: 5 white + 1 red (last)
            if (nums.size() >= 6) {
                whites = nums.subList(0, 5);
                reds = List.of(nums.get(5));
            }

            Integer mult = tryParseInt(cell(row, multCol));

            out.add(IngestedDraw.builder()
                    .gameModeId(gameModeId)
                    .stateCode(stateCode)
                    .drawDate(drawDate)
                    .whiteNumbers(new ArrayList<>(whites))
                    .redNumbers(reds == null ? null : new ArrayList<>(reds))
                    .multiplier(mult)
                    .build());
        }

        if (out.isEmpty()) {
            throw new BadRequestException("CSV parsed but no draws were recognized");
        }
        return out;
    }

    private static List<String> splitLines(String text) {
        String[] raw = text.split("\\r?\\n");
        List<String> out = new ArrayList<>(raw.length);
        for (String s : raw) if (s != null && !s.isBlank()) out.add(s);
        return out;
    }

    private static Map<String, Integer> indexHeader(List<String> header) {
        Map<String, Integer> m = new HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            m.put(norm(header.get(i)), i);
        }
        return m;
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

    private static LocalDate tryParseDate(String s) {
        if (s == null || s.isBlank()) return null;
        for (DateTimeFormatter f : DATE_FORMATS) {
            try { return LocalDate.parse(s.trim(), f); } catch (Exception ignored) { }
        }
        return null;
    }

    private static Integer tryParseInt(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return null; }
    }

    private static List<Integer> parseInts(String s) {
        if (s == null) return List.of();
        Matcher m = INT_PATTERN.matcher(s);
        List<Integer> out = new ArrayList<>();
        while (m.find()) out.add(Integer.parseInt(m.group()));
        return out;
    }

    private static List<String> parseCsvRow(String line) {
        List<String> out = new ArrayList<>();
        if (line == null) return out;

        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
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
        return out;
    }
}
