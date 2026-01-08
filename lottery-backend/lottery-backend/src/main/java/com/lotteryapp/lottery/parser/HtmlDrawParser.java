package com.lotteryapp.lottery.parser;

import com.lotteryapp.common.exception.BadRequestException;
import com.lotteryapp.lottery.domain.source.SourceType;
import com.lotteryapp.lottery.ingestion.model.IngestedDraw;
import org.springframework.stereotype.Component;

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

        // Split into chunks to increase chance date + numbers appear together
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

            out.add(IngestedDraw.builder()
                    .gameModeId(gameModeId)
                    .stateCode(stateCode)
                    .drawDate(date)
                    .whiteNumbers(new ArrayList<>(whites))
                    .redNumbers(new ArrayList<>(reds))
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
}
