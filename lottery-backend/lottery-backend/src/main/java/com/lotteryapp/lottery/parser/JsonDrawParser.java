package com.lotteryapp.lottery.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lotteryapp.common.exception.BadRequestException;
import com.lotteryapp.lottery.domain.source.SourceType;
import com.lotteryapp.lottery.ingestion.model.IngestedDraw;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class JsonDrawParser implements DrawParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // money like 100000000 or "$100,000,000"
    private static final Pattern MONEY = Pattern.compile("\\$?\s*([0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)");

    @Override
    public SourceType supportedSourceType() {
        return SourceType.JSON;
    }

    @Override
    public boolean supports(String parserKey) {
        return true;
    }

    @Override
    public List<IngestedDraw> parse(byte[] bytes, Long gameModeId, String stateCode) {
        if (bytes == null || bytes.length == 0) {
            throw new BadRequestException("Draw JSON is empty");
        }

        try {
            JsonNode root = MAPPER.readTree(bytes);

            List<JsonNode> nodes = new ArrayList<>();
            if (root.isArray()) {
                root.forEach(nodes::add);
            } else if (root.has("draws") && root.get("draws").isArray()) {
                root.get("draws").forEach(nodes::add);
            } else if (root.has("results") && root.get("results").isArray()) {
                root.get("results").forEach(nodes::add);
            } else {
                nodes.add(root);
            }

            List<IngestedDraw> out = new ArrayList<>();
            for (JsonNode n : nodes) {
                LocalDate date = readDate(n, "drawDate", "draw_date", "date");
                if (date == null) continue;

                List<Integer> white = readIntArray(n, "whiteNumbers", "white_numbers", "numbers", "winningNumbers");
                List<Integer> red = readIntArray(n, "redNumbers", "red_numbers", "powerball", "megaBall", "bonus");

                Integer mult = readInt(n, "multiplier", "powerPlay", "power_play", "megaplier");

                Long jackpot = readMoney(n, "jackpotAmount", "jackpot_amount", "jackpot", "estimatedJackpot", "estimated_jackpot");
                Long cash = readMoney(n, "cashValue", "cash_value", "cash", "estimatedCashValue", "estimated_cash_value");

                LocalTime drawTime = readTime(n, "drawTimeLocal", "draw_time_local", "drawTime", "draw_time", "time");
                String tz = readText(n, "drawTimeZoneId", "draw_time_zone_id", "timeZoneId", "time_zone_id", "timezone", "timeZone");

                out.add(IngestedDraw.builder()
                        .drawDate(date)
                        .whiteNumbers(white)
                        .redNumbers(red)
                        .multiplier(mult)
                        .jackpotAmount(jackpot)
                        .cashValue(cash)
                        .drawTimeLocal(drawTime)
                        .drawTimeZoneId(tz)
                        .build());
            }

            if (out.isEmpty()) {
                throw new BadRequestException("Parsed draw JSON but no draws found");
            }

            return out;
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("Failed to parse draw JSON: " + e.getMessage());
        }
    }

    private static LocalDate readDate(JsonNode n, String... keys) {
        for (String k : keys) {
            JsonNode v = n.get(k);
            if (v == null || v.isNull()) continue;
            String s = v.asText(null);
            if (s == null || s.isBlank()) continue;
            try {
                return LocalDate.parse(s.trim());
            } catch (Exception ignore) {
            }
        }
        return null;
    }

    private static List<Integer> readIntArray(JsonNode n, String... keys) {
        for (String k : keys) {
            JsonNode v = n.get(k);
            if (v == null || v.isNull()) continue;

            // sometimes it's a string "1 2 3 4 5"
            if (v.isTextual()) {
                String s = v.asText("");
                List<Integer> nums = extractInts(s);
                if (!nums.isEmpty()) return nums;
            }

            if (v.isArray()) {
                List<Integer> out = new ArrayList<>();
                for (JsonNode item : v) {
                    if (item == null || item.isNull()) continue;
                    if (item.canConvertToInt()) out.add(item.asInt());
                    else if (item.isTextual()) {
                        try { out.add(Integer.parseInt(item.asText().trim())); } catch (Exception ignore) {}
                    }
                }
                if (!out.isEmpty()) return out;
            }
        }
        return new ArrayList<>();
    }

    private static Integer readInt(JsonNode n, String... keys) {
        for (String k : keys) {
            JsonNode v = n.get(k);
            if (v == null || v.isNull()) continue;
            if (v.canConvertToInt()) return v.asInt();
            if (v.isTextual()) {
                try { return Integer.parseInt(v.asText().trim()); } catch (Exception ignore) {}
            }
        }
        return null;
    }

    private static String readText(JsonNode n, String... keys) {
        for (String k : keys) {
            JsonNode v = n.get(k);
            if (v == null || v.isNull()) continue;
            String s = v.asText(null);
            if (s != null && !s.isBlank()) return s.trim();
        }
        return null;
    }

    private static Long readMoney(JsonNode n, String... keys) {
        for (String k : keys) {
            JsonNode v = n.get(k);
            if (v == null || v.isNull()) continue;
            if (v.isNumber()) return v.asLong();
            if (v.isTextual()) {
                Long parsed = parseMoney(v.asText());
                if (parsed != null) return parsed;
            }
        }
        return null;
    }

    private static LocalTime readTime(JsonNode n, String... keys) {
        for (String k : keys) {
            JsonNode v = n.get(k);
            if (v == null || v.isNull()) continue;
            String s = v.asText(null);
            LocalTime t = parseTime(s);
            if (t != null) return t;
        }
        return null;
    }

    private static Long parseMoney(String s) {
        if (s == null) return null;
        Matcher m = MONEY.matcher(s.replaceAll("\s+", " ").trim());
        if (!m.find()) return null;
        try {
            return Long.parseLong(m.group(1).replace(",", ""));
        } catch (Exception e) {
            return null;
        }
    }

    private static LocalTime parseTime(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isBlank()) return null;

        // ISO HH:mm[:ss]
        try {
            if (t.length() >= 5 && t.charAt(2) == ':') {
                return LocalTime.parse(t.length() > 8 ? t.substring(0, 8) : t);
            }
        } catch (Exception ignore) {}

        // 12h like 10:59 PM
        Matcher m = Pattern.compile("\b(1[0-2]|0?[1-9]):([0-5]\\d)\s*(AM|PM)\b", Pattern.CASE_INSENSITIVE).matcher(t);
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

    private static List<Integer> extractInts(String s) {
        if (s == null) return List.of();
        Matcher m = Pattern.compile("\b\\d{1,2}\b").matcher(s);
        List<Integer> out = new ArrayList<>();
        while (m.find()) {
            try { out.add(Integer.parseInt(m.group(0))); } catch (Exception ignore) {}
        }
        return out;
    }
}
