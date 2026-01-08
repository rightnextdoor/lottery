package com.lotteryapp.lottery.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class JsonDrawParser implements DrawParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern INT = Pattern.compile("\\d+");

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("M/d/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("M-d-yyyy"),
            DateTimeFormatter.ofPattern("MM-d-yyyy")
    );

    @Override
    public SourceType supportedSourceType() {
        return SourceType.JSON;
    }

    @Override
    public boolean supports(String parserKey) {
        if (parserKey == null) return true;
        return parserKey.trim().toUpperCase(Locale.ROOT).contains("JSON");
    }

    @Override
    public List<IngestedDraw> parse(byte[] bytes, Long gameModeId, String stateCode) {
        if (bytes == null || bytes.length == 0) throw new BadRequestException("JSON response was empty");

        JsonNode root;
        try {
            root = MAPPER.readTree(bytes);
        } catch (Exception e) {
            String preview = new String(bytes, 0, Math.min(bytes.length, 300), StandardCharsets.UTF_8);
            throw new BadRequestException("Failed to parse JSON", Map.of("preview", preview));
        }

        JsonNode array = findFirstArray(root);
        if (array == null || !array.isArray()) {
            throw new BadRequestException("JSON does not contain an array of draws");
        }

        List<IngestedDraw> out = new ArrayList<>();
        for (JsonNode row : array) {
            if (!row.isObject()) continue;

            LocalDate date = parseDate(
                    text(row, "draw_date"),
                    text(row, "drawDate"),
                    text(row, "date")
            );
            if (date == null) continue;

            Integer mult = parseInt(text(row, "multiplier"), text(row, "power_play"), text(row, "megaplier"));

            // Try explicit bonus fields first
            Integer bonus = parseInt(
                    text(row, "powerball"),
                    text(row, "power_ball"),
                    text(row, "mega_ball"),
                    text(row, "megaball"),
                    text(row, "bonus"),
                    text(row, "bonus_ball")
            );

            List<Integer> nums = parseInts(text(row, "winning_numbers"), text(row, "winningNumbers"), text(row, "numbers"));
            if (nums.isEmpty()) {
                // fallback: scan all string values
                nums = parseInts(row.toString());
            }
            if (nums.isEmpty()) continue;

            List<Integer> whites;
            List<Integer> reds = null;

            if (bonus != null) {
                reds = List.of(bonus);
                // Remove bonus from end if it’s included in winning_numbers
                if (!nums.isEmpty() && Objects.equals(nums.get(nums.size() - 1), bonus)) {
                    nums = nums.subList(0, nums.size() - 1);
                }
            }

            if (nums.size() >= 5) {
                whites = nums.subList(0, 5);
                // If no explicit bonus but we have 6 nums, treat last as red
                if (reds == null && nums.size() >= 6) {
                    reds = List.of(nums.get(5));
                }
            } else {
                whites = nums;
            }

            out.add(IngestedDraw.builder()
                    .gameModeId(gameModeId)
                    .stateCode(stateCode)
                    .drawDate(date)
                    .whiteNumbers(new ArrayList<>(whites))
                    .redNumbers(reds == null ? null : new ArrayList<>(reds))
                    .multiplier(mult)
                    .build());
        }

        if (out.isEmpty()) throw new BadRequestException("JSON parsed but no draws recognized");
        return out;
    }

    private static JsonNode findFirstArray(JsonNode root) {
        if (root == null) return null;
        if (root.isArray()) return root;

        if (root.isObject()) {
            for (String k : List.of("data", "results", "items")) {
                JsonNode n = root.get(k);
                if (n != null && n.isArray()) return n;
            }
            // If object contains an array anywhere, take first
            Iterator<Map.Entry<String, JsonNode>> it = root.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                if (e.getValue() != null && e.getValue().isArray()) return e.getValue();
            }
        }
        return null;
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return null;
        return v.asText(null);
    }

    private static LocalDate parseDate(String... candidates) {
        for (String s : candidates) {
            LocalDate d = tryParseDate(s);
            if (d != null) return d;
        }
        return null;
    }

    private static LocalDate tryParseDate(String s) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim();
        for (DateTimeFormatter f : DATE_FORMATS) {
            try { return LocalDate.parse(t, f); } catch (Exception ignored) { }
        }
        return null;
    }

    private static Integer parseInt(String... candidates) {
        for (String s : candidates) {
            if (s == null || s.isBlank()) continue;
            try { return Integer.parseInt(s.trim()); } catch (NumberFormatException ignored) { }
        }
        return null;
    }

    private static List<Integer> parseInts(String... candidates) {
        for (String s : candidates) {
            List<Integer> nums = parseIntsOne(s);
            if (!nums.isEmpty()) return nums;
        }
        return List.of();
    }

    private static List<Integer> parseIntsOne(String s) {
        if (s == null) return List.of();
        Matcher m = INT.matcher(s);
        List<Integer> out = new ArrayList<>();
        while (m.find()) out.add(Integer.parseInt(m.group()));
        return out;
    }
}
