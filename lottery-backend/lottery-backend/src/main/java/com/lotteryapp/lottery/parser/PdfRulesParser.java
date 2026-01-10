package com.lotteryapp.lottery.parser;

import com.lotteryapp.common.exception.BadRequestException;
import com.lotteryapp.lottery.domain.source.SourceType;
import com.lotteryapp.lottery.ingestion.model.IngestedRules;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PdfRulesParser implements RulesParser {

    private static final Pattern INT = Pattern.compile("\b\\d{1,4}\b");

    private static final Pattern DATE_ISO = Pattern.compile("\b\\d{4}-\\d{2}-\\d{2}\b");
    private static final Pattern DATE_US = Pattern.compile("\b(\\d{1,2})/(\\d{1,2})/(\\d{4})\b");
    private static final Pattern DATE_LONG = Pattern.compile("\b(January|February|March|April|May|June|July|August|September|October|November|December)\s+(\\d{1,2}),\s*(\\d{4})\b", Pattern.CASE_INSENSITIVE);

    @Override
    public SourceType supportedSourceType() {
        return SourceType.PDF;
    }

    @Override
    public boolean supports(String parserKey) {
        return true;
    }

    @Override
    public IngestedRules parse(byte[] bytes, Long gameModeId, String stateCode) {
        if (bytes == null || bytes.length == 0) {
            throw new BadRequestException("Rules PDF is empty");
        }

        String text;
        try (PDDocument doc = PDDocument.load(new ByteArrayInputStream(bytes))) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            text = stripper.getText(doc);
        } catch (Exception e) {
            throw new BadRequestException("Failed to read rules PDF: " + e.getMessage());
        }

        // Very light heuristics: we look for the first reasonable ranges and pick counts.
        // This is intentionally conservative; if we can't determine a field, we leave it null
        // so the RulesService can decide whether to keep the DB value.

        Integer whitePick = findPickCount(text, "white", "main", "numbers");
        Integer whiteMin = findFirstIntNear(text, "from", "between");
        Integer whiteMax = findSecondIntNear(text, "from", "between");

        Integer redPick = findPickCount(text, "red", "powerball", "mega ball", "bonus");
        Integer redMin = null;
        Integer redMax = null;

        LocalDate formatStart = findFormatStartDate(text);

        Boolean whiteOrdered = inferOrdered(text);
        Boolean whiteRepeats = inferRepeats(text);

        // often same for red; if not, keep null
        Boolean redOrdered = whiteOrdered;
        Boolean redRepeats = whiteRepeats;

        return IngestedRules.builder()
                .formatStartDate(formatStart)
                .whitePickCount(whitePick)
                .whiteMin(whiteMin)
                .whiteMax(whiteMax)
                .whiteOrdered(whiteOrdered)
                .whiteAllowRepeats(whiteRepeats)
                .redPickCount(redPick)
                .redMin(redMin)
                .redMax(redMax)
                .redOrdered(redOrdered)
                .redAllowRepeats(redRepeats)
                .build();
    }

    private static Integer findPickCount(String text, String... keywords) {
        if (text == null) return null;
        String upper = text.toUpperCase(Locale.ROOT);

        for (String k : keywords) {
            int idx = upper.indexOf(k.toUpperCase(Locale.ROOT));
            if (idx < 0) continue;

            String window = upper.substring(idx, Math.min(upper.length(), idx + 200));
            Matcher m = INT.matcher(window);
            while (m.find()) {
                int v = Integer.parseInt(m.group());
                if (v >= 1 && v <= 20) return v;
            }
        }
        return null;
    }

    private static Integer findFirstIntNear(String text, String... keywords) {
        Integer[] pair = findRangePair(text, keywords);
        return pair == null ? null : pair[0];
    }

    private static Integer findSecondIntNear(String text, String... keywords) {
        Integer[] pair = findRangePair(text, keywords);
        return pair == null ? null : pair[1];
    }

    private static Integer[] findRangePair(String text, String... keywords) {
        if (text == null) return null;
        String upper = text.toUpperCase(Locale.ROOT);

        for (String k : keywords) {
            int idx = upper.indexOf(k.toUpperCase(Locale.ROOT));
            if (idx < 0) continue;

            String window = upper.substring(idx, Math.min(upper.length(), idx + 300));
            Matcher m = INT.matcher(window);
            Integer a = null, b = null;
            while (m.find()) {
                int v = Integer.parseInt(m.group());
                if (v < 1 || v > 9999) continue;
                if (a == null) a = v;
                else { b = v; break; }
            }
            if (a != null && b != null) return new Integer[]{a, b};
        }

        return null;
    }

    private static LocalDate findFormatStartDate(String text) {
        if (text == null) return null;
        String t = text;

        // try ISO
        Matcher mi = DATE_ISO.matcher(t);
        if (mi.find()) {
            try { return LocalDate.parse(mi.group()); } catch (Exception ignore) {}
        }

        // try US
        Matcher mu = DATE_US.matcher(t);
        if (mu.find()) {
            try {
                int mm = Integer.parseInt(mu.group(1));
                int dd = Integer.parseInt(mu.group(2));
                int yy = Integer.parseInt(mu.group(3));
                return LocalDate.of(yy, mm, dd);
            } catch (Exception ignore) {}
        }

        // long date
        Matcher ml = DATE_LONG.matcher(t);
        if (ml.find()) {
            try {
                String month = ml.group(1);
                int day = Integer.parseInt(ml.group(2));
                int year = Integer.parseInt(ml.group(3));
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH);
                int monthNum = java.time.Month.valueOf(month.toUpperCase(Locale.ROOT)).getValue();
                return LocalDate.of(year, monthNum, day);
            } catch (Exception ignore) {}
        }

        return null;
    }

    private static Boolean inferOrdered(String text) {
        if (text == null) return null;
        String u = text.toUpperCase(Locale.ROOT);
        if (u.contains("IN ANY ORDER") || u.contains("ORDER DOES NOT MATTER")) return false;
        if (u.contains("IN EXACT ORDER") || u.contains("IN THE EXACT ORDER")) return true;
        return null;
    }

    private static Boolean inferRepeats(String text) {
        if (text == null) return null;
        String u = text.toUpperCase(Locale.ROOT);
        if (u.contains("MAY BE REPEATED") || u.contains("CAN BE REPEATED") || u.contains("REPEATS ALLOWED")) return true;
        if (u.contains("NO NUMBER MAY BE REPEATED") || u.contains("NOT BE REPEATED") || u.contains("REPEATS NOT ALLOWED")) return false;
        return null;
    }
}
