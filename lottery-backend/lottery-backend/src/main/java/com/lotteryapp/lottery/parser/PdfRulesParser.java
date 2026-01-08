package com.lotteryapp.lottery.parser;

import com.lotteryapp.common.exception.BadRequestException;
import com.lotteryapp.lottery.domain.source.SourceType;
import com.lotteryapp.lottery.ingestion.model.IngestedRules;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PdfRulesParser implements RulesParser {

    private static final Pattern CHOOSE_FROM_TO = Pattern.compile(
            "(?i)choose\\s+(\\d+)\\s+numbers?\\s+from\\s+(\\d+)\\s+(?:to|through|thru)\\s+(\\d+)"
    );

    private static final Pattern BONUS_FROM_TO = Pattern.compile(
            "(?i)choose\\s+(\\d+)\\s+(?:powerball|power\\s*ball|mega\\s*ball|megaball|bonus\\s*ball|bonus)\\s+numbers?\\s+from\\s+(\\d+)\\s+(?:to|through|thru)\\s+(\\d+)"
    );

    @Override
    public SourceType supportedSourceType() {
        return SourceType.PDF;
    }

    @Override
    public boolean supports(String parserKey) {
        if (parserKey == null) return true;
        String k = parserKey.trim().toUpperCase(Locale.ROOT);
        return k.contains("RULE") || k.contains("PDF");
    }

    @Override
    public IngestedRules parse(byte[] bytes, Long gameModeId, String stateCode) {
        if (bytes == null || bytes.length == 0) {
            throw new BadRequestException("Rules PDF is empty");
        }

        String text = extractText(bytes);
        String normalized = normalize(text);

        Match white = findFirst(CHOOSE_FROM_TO, normalized);
        if (white == null) {
            throw new BadRequestException("Could not find white-ball rule pattern in PDF");
        }

        Match red = findFirst(BONUS_FROM_TO, normalized);

        IngestedRules.IngestedRulesBuilder b = IngestedRules.builder()
                .gameModeId(gameModeId)
                .stateCode(stateCode)
                .whitePickCount(white.pickCount)
                .whiteMin(white.min)
                .whiteMax(white.max);

        if (red != null) {
            b.redPickCount(red.pickCount).redMin(red.min).redMax(red.max);
        }

        return b.build();
    }

    private static String extractText(byte[] bytes) {
        try (PDDocument doc = PDDocument.load(new ByteArrayInputStream(bytes))) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(doc);
        } catch (Exception e) {
            throw new BadRequestException("Failed to read rules PDF");
        }
    }

    private static String normalize(String s) {
        if (s == null) return "";
        String x = s.replace('\u00A0', ' ');
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

    private record Match(int pickCount, int min, int max) {}
}
