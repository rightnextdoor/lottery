package com.lotteryapp.lottery.parser;

import com.lotteryapp.common.exception.BadRequestException;
import com.lotteryapp.lottery.domain.source.SourceType;
import com.lotteryapp.lottery.ingestion.model.IngestedDraw;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class HtmlDrawParser implements DrawParser {

    private static final Pattern DATE_US = Pattern.compile("\\b(\\d{1,2})/(\\d{1,2})/(\\d{4})\\b");
    private static final Pattern DATE_ISO = Pattern.compile("\\b(\\d{4})-(\\d{2})-(\\d{2})\\b");
    private static final Pattern INT = Pattern.compile("\\b\\d{1,2}\\b");

    private static final Pattern MONEY_DOLLAR = Pattern.compile(
            "\\$\\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]+)?|[0-9]+(?:\\.[0-9]+)?)\\s*(BILLION|MILLION|B|M)?",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern MONEY_WORD = Pattern.compile(
            "\\b([0-9]+(?:\\.[0-9]+)?)\\s*(BILLION|MILLION)\\b",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    public SourceType supportedSourceType() {
        return SourceType.HTML;
    }

    @Override
    public boolean supports(String parserKey) {
        if (parserKey == null) return true;
        return parserKey.trim().toUpperCase(Locale.ROOT).contains("HTML");
    }

    @Override
    public List<IngestedDraw> parse(byte[] bytes, Long gameModeId, String stateCode) {
        if (bytes == null || bytes.length == 0) throw new BadRequestException("HTML response was empty");

        String html = new String(bytes, StandardCharsets.UTF_8);
        String text = html.replaceAll("<[^>]*>", " ").replaceAll("\\s{2,}", " ").trim();

        String[] chunks = text.split("(?i)draw|results|winning");
        List<IngestedDraw> out = new ArrayList<>();

        for (String chunk : chunks) {
            LocalDate date = parseDate(chunk);
            if (date == null) continue;

            List<Integer> nums = parseInts(chunk);
            if (nums.size() < 6) continue;

            nums = nums.subList(0, 6);
            List<Integer> whites = nums.subList(0, 5);
            List<Integer> reds = List.of(nums.get(5));

            BigDecimal jackpot = parseMoneyNear(chunk, "JACKPOT");
            BigDecimal cash = parseMoneyNear(chunk, "CASH");

            out.add(IngestedDraw.builder()
                    .gameModeId(gameModeId)
                    .stateCode(stateCode)
                    .drawDate(date)
                    .whiteNumbers(new ArrayList<>(whites))
                    .redNumbers(new ArrayList<>(reds))
                    .jackpotAmount(jackpot)
                    .cashValue(cash)
                    .build());
        }

        if (out.isEmpty()) throw new BadRequestException("HTML parsed but no draws were recognized");
        return out;
    }

    private static LocalDate parseDate(String s) {
        Matcher m1 = DATE_US.matcher(s);
        if (m1.find()) {
            int mm = Integer.parseInt(m1.group(1));
            int dd = Integer.parseInt(m1.group(2));
            int yy = Integer.parseInt(m1.group(3));
            return LocalDate.of(yy, mm, dd);
        }
        Matcher m2 = DATE_ISO.matcher(s);
        if (m2.find()) {
            int yy = Integer.parseInt(m2.group(1));
            int mm = Integer.parseInt(m2.group(2));
            int dd = Integer.parseInt(m2.group(3));
            return LocalDate.of(yy, mm, dd);
        }
        return null;
    }

    private static List<Integer> parseInts(String s) {
        Matcher m = INT.matcher(s);
        List<Integer> out = new ArrayList<>();
        while (m.find()) out.add(Integer.parseInt(m.group()));
        return out;
    }

    private static BigDecimal parseMoneyNear(String chunk, String keywordUpper) {
        if (chunk == null) return null;
        String upper = chunk.toUpperCase(Locale.ROOT);
        int idx = upper.indexOf(keywordUpper);
        if (idx < 0) return null;

        int start = Math.max(0, idx - 80);
        int end = Math.min(chunk.length(), idx + 200);
        String window = chunk.substring(start, end);

        Matcher md = MONEY_DOLLAR.matcher(window);
        if (md.find()) return scaleMoney(md.group(1), md.group(2));

        Matcher mw = MONEY_WORD.matcher(window);
        if (mw.find()) return scaleMoney(mw.group(1), mw.group(2));

        return null;
    }

    private static BigDecimal scaleMoney(String number, String scale) {
        if (number == null) return null;
        String n = number.replace(",", "").trim();
        BigDecimal base;
        try { base = new BigDecimal(n); } catch (Exception e) { return null; }

        if (scale == null || scale.isBlank()) return base;

        String sc = scale.trim().toUpperCase(Locale.ROOT);
        if (sc.startsWith("B")) return base.multiply(BigDecimal.valueOf(1_000_000_000L));
        if (sc.startsWith("M")) return base.multiply(BigDecimal.valueOf(1_000_000L));
        return base;
    }
}
