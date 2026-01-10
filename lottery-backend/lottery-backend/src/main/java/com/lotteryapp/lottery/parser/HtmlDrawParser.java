package com.lotteryapp.lottery.parser;

import com.lotteryapp.common.exception.BadRequestException;
import com.lotteryapp.lottery.domain.source.SourceType;
import com.lotteryapp.lottery.ingestion.model.IngestedDraw;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class HtmlDrawParser implements DrawParser {

    private static final Pattern DATE = Pattern.compile("\\b\\d{4}-\\d{2}-\\d{2}\\b");
    private static final Pattern NUMBERS = Pattern.compile("\\b\\d{1,2}\\b");
    private static final Pattern MULT = Pattern.compile("\b(multiplier|powerplay|power play|megaplier)\b[^0-9]{0,30}(\\d{1,2})", Pattern.CASE_INSENSITIVE);

    private static final Pattern JACKPOT = Pattern.compile("\b(jackpot|estimated jackpot)\b[^$0-9]{0,40}\\$?\s*([0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CASH = Pattern.compile("\b(cash value|estimated cash value|cash)\b[^$0-9]{0,40}\\$?\s*([0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern TIME_12H = Pattern.compile("\b(1[0-2]|0?[1-9]):([0-5]\\d)\s*(AM|PM)\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern IANA_TZ = Pattern.compile("\b[A-Za-z]+/[A-Za-z_]+\b");
    private static final Pattern TZ_ABBR = Pattern.compile("\b(ET|EST|EDT|CT|CST|CDT|MT|MST|MDT|PT|PST|PDT)\b");

    @Override
    public SourceType supportedSourceType() {
        return SourceType.HTML;
    }

    @Override
    public boolean supports(String parserKey) {
        // generic
        return true;
    }

    @Override
    public List<IngestedDraw> parse(byte[] bytes, Long gameModeId, String stateCode) {
        if (bytes == null || bytes.length == 0) {
            throw new BadRequestException("HTML is empty");
        }

        String html = new String(bytes, StandardCharsets.UTF_8);
        // keep original for regex (some sites embed JSON inside HTML)
        String text = html.replaceAll("<[^>]*>", " ");

        List<LocalDate> dates = new ArrayList<>();
        Matcher dm = DATE.matcher(text);
        while (dm.find()) {
            try { dates.add(LocalDate.parse(dm.group())); } catch (Exception ignore) {}
        }

        // naive: find number groups separated by 5+ numbers; we keep compatibility with existing behavior:
        List<List<Integer>> numberGroups = new ArrayList<>();
        List<Integer> current = new ArrayList<>();
        Matcher nm = NUMBERS.matcher(text);
        while (nm.find()) {
            int v = Integer.parseInt(nm.group());
            current.add(v);
            if (current.size() >= 6) { // enough for common games; flush in chunks of 6
                numberGroups.add(new ArrayList<>(current.subList(0, 6)));
                current.clear();
            }
        }

        Integer mult = parseMultiplier(text);

        Long jackpot = parseMoneyByPattern(text, JACKPOT);
        Long cash = parseMoneyByPattern(text, CASH);

        LocalTime drawTime = parseTime(text);
        String tz = parseTimeZoneId(text);

        int count = Math.min(dates.size(), numberGroups.size());
        List<IngestedDraw> out = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            List<Integer> nums = numberGroups.get(i);
            List<Integer> whites = nums.subList(0, Math.min(5, nums.size()));
            List<Integer> reds = nums.size() > 5 ? List.of(nums.get(5)) : List.of();

            out.add(IngestedDraw.builder()
                    .drawDate(dates.get(i))
                    .whiteNumbers(new ArrayList<>(whites))
                    .redNumbers(new ArrayList<>(reds))
                    .multiplier(mult)
                    .jackpotAmount(jackpot)
                    .cashValue(cash)
                    .drawTimeLocal(drawTime)
                    .drawTimeZoneId(tz)
                    .build());
        }

        if (out.isEmpty()) {
            throw new BadRequestException("HTML parsed but no draws were recognized");
        }

        return out;
    }

    private static Integer parseMultiplier(String s) {
        if (s == null) return null;
        Matcher m = MULT.matcher(s);
        if (m.find()) {
            try { return Integer.parseInt(m.group(2)); } catch (Exception ignore) {}
        }
        return null;
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
