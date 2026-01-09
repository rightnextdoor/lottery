package com.lotteryapp.lottery.parser;

import com.lotteryapp.common.exception.BadRequestException;
import com.lotteryapp.lottery.domain.source.SourceType;
import com.lotteryapp.lottery.ingestion.model.IngestedRules;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.Month;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class HtmlRulesParser implements RulesParser {

    // Common “pick/choose N numbers from A to B” patterns
    private static final Pattern CHOOSE_FROM_TO = Pattern.compile(
            "(?i)(?:choose|pick)\\s+(\\d+)\\s+numbers?\\s+from\\s+(\\d+)\\s+(?:to|through|thru|-)\\s+(\\d+)"
    );

    // Bonus / red ball patterns
    private static final Pattern BONUS_FROM_TO = Pattern.compile(
            "(?i)(?:choose|pick)\\s+(\\d+)\\s+(?:power\\s*ball|powerball|mega\\s*ball|megaball|bonus(?:\\s+ball)?|ball)\\s+(?:number|numbers)?\\s*from\\s+(\\d+)\\s+(?:to|through|thru|-)\\s+(\\d+)"
    );

    // Date patterns for format start
    private static final Pattern DATE_MDY = Pattern.compile(
            "(?i)(?:effective|beginning|starting|as of)\\s+(\\d{1,2})/(\\d{1,2})/(\\d{4})"
    );
    private static final Pattern DATE_MONTH_NAME = Pattern.compile(
            "(?i)(?:effective|beginning|starting|as of)\\s+([A-Za-z]+)\\s+(\\d{1,2}),\\s*(\\d{4})"
    );

    @Override
    public SourceType supportedSourceType() {
        return SourceType.HTML;
    }

    @Override
    public boolean supports(String parserKey) {
        // Safe default: allow as fallback for HTML rules sources.
        return true;
    }

    @Override
    public IngestedRules parse(byte[] bytes, Long gameModeId, String stateCode) {
        if (bytes == null || bytes.length == 0) throw new BadRequestException("Rules HTML is empty");

        String raw = new String(bytes, StandardCharsets.UTF_8);
        String text = normalize(raw);

        Match white = findFirst(CHOOSE_FROM_TO, text);
        if (white == null) throw new BadRequestException("Could not find white-ball rule pattern in HTML");

        Match red = findFirst(BONUS_FROM_TO, text);

        Map<String, Object> meta = new LinkedHashMap<>();

        LocalDate formatStartDate = parseFormatStartDate(text, meta);

        // ordered/repeats heuristics (try -> infer -> default)
        Boolean whiteOrdered = parseOrdered(text, meta, "whiteOrdered");
        Boolean whiteRepeats = parseRepeats(text, meta, "whiteAllowRepeats");

        Boolean redOrdered = parseOrdered(text, meta, "redOrdered");
        Boolean redRepeats = parseRepeats(text, meta, "redAllowRepeats");

        IngestedRules.IngestedRulesBuilder b = IngestedRules.builder()
                .gameModeId(gameModeId)
                .stateCode(stateCode)
                .meta(meta)
                .formatStartDate(formatStartDate)
                .whitePickCount(white.pickCount)
                .whiteMin(white.min)
                .whiteMax(white.max)
                .whiteOrdered(whiteOrdered)
                .whiteAllowRepeats(whiteRepeats);

        if (red != null) {
            b.redPickCount(red.pickCount)
                    .redMin(red.min)
                    .redMax(red.max)
                    .redOrdered(redOrdered)
                    .redAllowRepeats(redRepeats);
        }

        return b.build();
    }

    // --------------------
    // Helpers
    // --------------------

    private static String normalize(String s) {
        if (s == null) return "";
        String x = s.replace('\u00A0', ' ');
        x = x.replaceAll("(?s)<script.*?</script>", " ");
        x = x.replaceAll("(?s)<style.*?</style>", " ");
        x = x.replaceAll("<[^>]+>", " ");
        x = x.replaceAll("[\\r\\n\\t]+", " ");
        x = x.replaceAll("\\s{2,}", " ").trim();
        return x;
    }

    private static Match findFirst(Pattern p, String text) {
        Matcher m = p.matcher(text);
        if (!m.find()) return null;
        return new Match(
                Integer.parseInt(m.group(1)),
                Integer.parseInt(m.group(2)),
                Integer.parseInt(m.group(3))
        );
    }

    private static LocalDate parseFormatStartDate(String text, Map<String, Object> meta) {
        Matcher m1 = DATE_MDY.matcher(text);
        if (m1.find()) {
            int mm = Integer.parseInt(m1.group(1));
            int dd = Integer.parseInt(m1.group(2));
            int yy = Integer.parseInt(m1.group(3));
            meta.put("formatStartDateMatch", "MDY");
            return LocalDate.of(yy, mm, dd);
        }

        Matcher m2 = DATE_MONTH_NAME.matcher(text);
        if (m2.find()) {
            Month month = parseMonth(m2.group(1));
            int dd = Integer.parseInt(m2.group(2));
            int yy = Integer.parseInt(m2.group(3));
            meta.put("formatStartDateMatch", "MONTH_NAME");
            return LocalDate.of(yy, month, dd);
        }

        meta.put("formatStartDateMatch", "NOT_FOUND");
        return null;
    }

    private static Month parseMonth(String s) {
        String x = s.trim().toUpperCase(Locale.ROOT);
        return switch (x) {
            case "JAN", "JANUARY" -> Month.JANUARY;
            case "FEB", "FEBRUARY" -> Month.FEBRUARY;
            case "MAR", "MARCH" -> Month.MARCH;
            case "APR", "APRIL" -> Month.APRIL;
            case "MAY" -> Month.MAY;
            case "JUN", "JUNE" -> Month.JUNE;
            case "JUL", "JULY" -> Month.JULY;
            case "AUG", "AUGUST" -> Month.AUGUST;
            case "SEP", "SEPT", "SEPTEMBER" -> Month.SEPTEMBER;
            case "OCT", "OCTOBER" -> Month.OCTOBER;
            case "NOV", "NOVEMBER" -> Month.NOVEMBER;
            case "DEC", "DECEMBER" -> Month.DECEMBER;
            default -> throw new BadRequestException("Unknown month name: " + s);
        };
    }

    private static Boolean parseOrdered(String text, Map<String, Object> meta, String key) {
        String upper = text.toUpperCase(Locale.ROOT);

        if (upper.contains("IN ANY ORDER")) {
            meta.put(key + "Source", "EXPLICIT_IN_ANY_ORDER");
            return false;
        }
        if (upper.contains("EXACT ORDER") || upper.contains("IN EXACT ORDER") || upper.contains("ORDER MATTERS")) {
            meta.put(key + "Source", "EXPLICIT_EXACT_ORDER");
            return true;
        }

        // Inference default for lotteries
        meta.put(key + "Source", "INFERRED_DEFAULT_FALSE");
        return false;
    }

    private static Boolean parseRepeats(String text, Map<String, Object> meta, String key) {
        String upper = text.toUpperCase(Locale.ROOT);

        if (upper.contains("WITH REPLACEMENT") || upper.contains("REPEATS ALLOWED") || upper.contains("MAY BE REPEATED")) {
            meta.put(key + "Source", "EXPLICIT_REPEATS_ALLOWED");
            return true;
        }
        if (upper.contains("WITHOUT REPLACEMENT") || upper.contains("NO REPEATS") || upper.contains("DIFFERENT NUMBERS")) {
            meta.put(key + "Source", "EXPLICIT_NO_REPEATS");
            return false;
        }

        // Inference default for lotteries
        meta.put(key + "Source", "INFERRED_DEFAULT_FALSE");
        return false;
    }

    private record Match(int pickCount, int min, int max) {}
}
