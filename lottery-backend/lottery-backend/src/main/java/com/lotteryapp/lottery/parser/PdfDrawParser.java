package com.lotteryapp.lottery.parser;

import com.lotteryapp.common.exception.BadRequestException;
import com.lotteryapp.lottery.domain.source.SourceType;
import com.lotteryapp.lottery.ingestion.model.IngestedDraw;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PdfDrawParser implements DrawParser {

    private static final Pattern DATE_ISO = Pattern.compile("\b\\d{4}-\\d{2}-\\d{2}\b");
    private static final Pattern DATE_US = Pattern.compile("\b(\\d{1,2})/(\\d{1,2})/(\\d{4})\b");
    private static final Pattern INT = Pattern.compile("\b\\d{1,2}\b");

    private static final Pattern JACKPOT = Pattern.compile("\b(jackpot|estimated jackpot)\b[^$0-9]{0,40}\\$?\s*([0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CASH = Pattern.compile("\b(cash value|estimated cash value|cash)\b[^$0-9]{0,40}\\$?\s*([0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern MULT = Pattern.compile("\b(multiplier|powerplay|power play|megaplier)\b[^0-9]{0,30}(\\d{1,2})", Pattern.CASE_INSENSITIVE);

    private static final Pattern TIME_12H = Pattern.compile("\b(1[0-2]|0?[1-9]):([0-5]\\d)\s*(AM|PM)\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern IANA_TZ = Pattern.compile("\b[A-Za-z]+/[A-Za-z_]+\b");
    private static final Pattern TZ_ABBR = Pattern.compile("\b(ET|EST|EDT|CT|CST|CDT|MT|MST|MDT|PT|PST|PDT)\b");

    @Override
    public SourceType supportedSourceType() {
        return SourceType.PDF;
    }

    @Override
    public boolean supports(String parserKey) {
        return true;
    }

    @Override
    public List<IngestedDraw> parse(byte[] bytes, Long gameModeId, String stateCode) {
        if (bytes == null || bytes.length == 0) {
            throw new BadRequestException("PDF is empty");
        }

        String text;
        try (PDDocument doc = PDDocument.load(new ByteArrayInputStream(bytes))) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            text = stripper.getText(doc);
        } catch (Exception e) {
            throw new BadRequestException("Failed to read PDF: " + e.getMessage());
        }

        LocalDate date = parseDate(text);
        List<Integer> nums = parseInts(text);

        // best-effort metadata
        Integer mult = parseMultiplier(text);
        Long jackpot = parseMoneyByPattern(text, JACKPOT);
        Long cash = parseMoneyByPattern(text, CASH);
        LocalTime drawTime = parseTime(text);
        String tz = parseTimeZoneId(text);

        if (date == null || nums.size() < 6) {
            throw new BadRequestException("PDF parsed but draw date or numbers were not found");
        }

        List<Integer> whites = nums.subList(0, Math.min(5, nums.size()));
        List<Integer> reds = nums.size() > 5 ? List.of(nums.get(5)) : List.of();

        return List.of(
                IngestedDraw.builder()
                        .drawDate(date)
                        .whiteNumbers(new ArrayList<>(whites))
                        .redNumbers(new ArrayList<>(reds))
                        .multiplier(mult)
                        .jackpotAmount(jackpot)
                        .cashValue(cash)
                        .drawTimeLocal(drawTime)
                        .drawTimeZoneId(tz)
                        .build()
        );
    }

    private static Integer parseMultiplier(String s) {
        if (s == null) return null;
        Matcher m = MULT.matcher(s);
        if (m.find()) {
            try { return Integer.parseInt(m.group(2)); } catch (Exception ignore) {}
        }
        return null;
    }

    private static LocalDate parseDate(String s) {
        if (s == null) return null;

        Matcher m = DATE_ISO.matcher(s);
        if (m.find()) {
            try { return LocalDate.parse(m.group()); } catch (Exception ignore) {}
        }

        Matcher m2 = DATE_US.matcher(s);
        if (m2.find()) {
            try {
                int mm = Integer.parseInt(m2.group(1));
                int dd = Integer.parseInt(m2.group(2));
                int yy = Integer.parseInt(m2.group(3));
                return LocalDate.of(yy, mm, dd);
            } catch (Exception ignore) {}
        }

        return null;
    }

    private static List<Integer> parseInts(String s) {
        Matcher m = INT.matcher(s);
        List<Integer> out = new ArrayList<>();
        while (m.find()) {
            try { out.add(Integer.parseInt(m.group())); } catch (Exception ignore) {}
        }
        return out;
    }

    private static Long parseMoneyByPattern(String s, Pattern p) {
        if (s == null) return null;
        Matcher m = p.matcher(s);
        if (!m.find()) return null;
        try {
            return Long.parseLong(m.group(2).replace(",", ""));
        } catch (Exception ignore) {
            return null;
        }
    }

    private static LocalTime parseTime(String s) {
        if (s == null) return null;
        Matcher m = TIME_12H.matcher(s);
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

    private static String parseTimeZoneId(String s) {
        if (s == null) return null;

        Matcher mi = IANA_TZ.matcher(s);
        if (mi.find()) return mi.group(0);

        Matcher ma = TZ_ABBR.matcher(s.toUpperCase(Locale.ROOT));
        if (ma.find()) return ma.group(1);

        return null;
    }
}
