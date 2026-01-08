package com.lotteryapp.lottery.ingestion;

import com.lotteryapp.common.exception.BadRequestException;
import com.lotteryapp.common.exception.IngestionFailureException;
import com.lotteryapp.common.exception.IngestionFailureReason;
import com.lotteryapp.common.exception.NotFoundException;
import com.lotteryapp.lottery.domain.source.Source;
import com.lotteryapp.lottery.domain.source.SourceType;
import com.lotteryapp.lottery.ingestion.model.*;
import com.lotteryapp.lottery.ingestion.source.DrawSourceClient;
import com.lotteryapp.lottery.parser.DrawParser;
import com.lotteryapp.lottery.parser.DrawParserRegistry;
import com.lotteryapp.lottery.parser.HtmlScheduleParser;
import com.lotteryapp.lottery.parser.PdfRulesParser;
import com.lotteryapp.lottery.repository.SourceRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Service
public class IngestionService {

    private static final String MULTI = "MULTI";

    private final SourceRepository sourceRepository;
    private final DrawSourceClient drawSourceClient;

    private final DrawParserRegistry drawParserRegistry;
    private final PdfRulesParser pdfRulesParser;
    private final HtmlScheduleParser htmlScheduleParser;

    public IngestionService(
            SourceRepository sourceRepository,
            DrawSourceClient drawSourceClient,
            DrawParserRegistry drawParserRegistry,
            PdfRulesParser pdfRulesParser,
            HtmlScheduleParser htmlScheduleParser
    ) {
        this.sourceRepository = sourceRepository;
        this.drawSourceClient = drawSourceClient;
        this.drawParserRegistry = drawParserRegistry;
        this.pdfRulesParser = pdfRulesParser;
        this.htmlScheduleParser = htmlScheduleParser;
    }

    // -----------------------------
    // DRAW
    // -----------------------------

    public IngestedDraw ingestLatestDraw(Long gameModeId, String stateCode) {
        List<IngestedDraw> draws = ingestDraws(gameModeId, stateCode, IngestionCapability.DRAW_LATEST, null);
        return draws.stream()
                .max(Comparator.comparing(IngestedDraw::getDrawDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElseThrow(() -> new NotFoundException("No draw found"));
    }

    public IngestedDraw ingestDrawByDate(Long gameModeId, String stateCode, LocalDate date) {
        if (date == null) throw new BadRequestException("date is required");
        List<IngestedDraw> draws = ingestDraws(gameModeId, stateCode, IngestionCapability.DRAW_BY_DATE, date);
        return draws.stream()
                .filter(d -> date.equals(d.getDrawDate()))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("No draw found for date"));
    }

    public List<IngestedDraw> ingestDrawHistory(Long gameModeId, String stateCode) {
        return ingestDraws(gameModeId, stateCode, IngestionCapability.DRAW_HISTORY, null);
    }

    private List<IngestedDraw> ingestDraws(Long gameModeId, String stateCode, IngestionCapability cap, LocalDate requestedDate) {
        List<Source> sources = loadSources(gameModeId, stateCode);
        List<IngestionFailure.Attempt> attempts = new ArrayList<>();

        for (Source src : sources) {
            if (!supports(src, cap)) continue;

            String url = expandUrl(src.getUrlTemplate(), requestedDate);

            try {
                DrawSourceClient.FetchedContent fetched = drawSourceClient.fetch(url, Map.of(
                        "Accept", acceptHeaderFor(src.getSourceType())
                ));

                DrawParser parser = drawParserRegistry.resolve(src.getSourceType(), src.getParserKey());
                List<IngestedDraw> parsed = parser.parse(fetched.bytes(), gameModeId, normState(stateCode));

                Instant now = Instant.now();
                for (IngestedDraw d : parsed) {
                    d.setGameModeId(gameModeId);
                    d.setStateCode(normState(stateCode));
                    d.setSourceId(src.getId());
                    d.setFetchedAt(now);
                    d.setMeta(meta(src, fetched));
                }

                if (requestedDate != null) {
                    parsed = parsed.stream().filter(d -> requestedDate.equals(d.getDrawDate())).toList();
                }

                if (!parsed.isEmpty()) return parsed;

                attempts.add(attempt(src, url, fetched, "Parsed but no matching draw found"));
            } catch (Exception e) {
                attempts.add(attempt(src, url, null, safeMsg(e)));
            }
        }

        throw ingestionFailure(
                "All draw sources failed",
                cap.name(),
                gameModeId,
                stateCode,
                attempts
        );
    }

    // -----------------------------
    // RULES
    // -----------------------------

    public IngestedRules ingestRules(Long gameModeId, String stateCode) {
        List<Source> sources = loadSources(gameModeId, stateCode);
        List<IngestionFailure.Attempt> attempts = new ArrayList<>();

        for (Source src : sources) {
            if (!src.isSupportsRules()) continue;

            String url = expandUrl(src.getUrlTemplate(), null);

            try {
                DrawSourceClient.FetchedContent fetched = drawSourceClient.fetch(url, Map.of(
                        "Accept", acceptHeaderFor(src.getSourceType())
                ));

                IngestedRules rules = switch (src.getSourceType()) {
                    case PDF -> pdfRulesParser.parse(fetched.bytes(), gameModeId, normState(stateCode));
                    default -> throw new BadRequestException("No rules parser implemented for sourceType=" + src.getSourceType());
                };

                rules.setGameModeId(gameModeId);
                rules.setStateCode(normState(stateCode));
                rules.setSourceId(src.getId());
                rules.setFetchedAt(Instant.now());
                rules.setMeta(meta(src, fetched));
                return rules;

            } catch (Exception e) {
                attempts.add(attempt(src, url, null, safeMsg(e)));
            }
        }

        throw ingestionFailure(
                "All rules sources failed",
                IngestionCapability.RULES.name(),
                gameModeId,
                stateCode,
                attempts
        );
    }

    // -----------------------------
    // SCHEDULE
    // -----------------------------

    public IngestedSchedule ingestSchedule(Long gameModeId, String stateCode) {
        List<Source> sources = loadSources(gameModeId, stateCode);
        List<IngestionFailure.Attempt> attempts = new ArrayList<>();

        for (Source src : sources) {
            if (!src.isSupportsSchedule()) continue;

            String url = expandUrl(src.getUrlTemplate(), null);

            try {
                DrawSourceClient.FetchedContent fetched = drawSourceClient.fetch(url, Map.of(
                        "Accept", acceptHeaderFor(src.getSourceType())
                ));

                IngestedSchedule sched = switch (src.getSourceType()) {
                    case HTML -> htmlScheduleParser.parse(fetched.bytes(), gameModeId, normState(stateCode));
                    default -> throw new BadRequestException("No schedule parser implemented for sourceType=" + src.getSourceType());
                };

                sched.setGameModeId(gameModeId);
                sched.setStateCode(normState(stateCode));
                sched.setSourceId(src.getId());
                sched.setFetchedAt(Instant.now());
                sched.setMeta(meta(src, fetched));
                return sched;

            } catch (Exception e) {
                attempts.add(attempt(src, url, null, safeMsg(e)));
            }
        }

        throw ingestionFailure(
                "All schedule sources failed",
                IngestionCapability.SCHEDULE.name(),
                gameModeId,
                stateCode,
                attempts
        );
    }

    // -----------------------------
    // GAME LIST
    // -----------------------------

    public IngestedGameList ingestGameList(String stateCode) {
        String st = normState(stateCode);
        if (st == null) throw new BadRequestException("stateCode is required");

        // This will be wired once you add real game-list sources/parsers.
        // For now, we fail with the correct ingestion failure shape (not BadRequest).
        throw new IngestionFailureException(
                "Game list ingestion not implemented yet",
                IngestionFailureReason.MANUAL_ENTRY_REQUIRED,
                IngestionFailureReason.MANUAL_ENTRY_REQUIRED.name(),
                Map.of("capability", IngestionCapability.GAME_LIST.name(), "stateCode", st)
        );
    }

    // -----------------------------
    // helpers
    // -----------------------------

    private List<Source> loadSources(Long gameModeId, String stateCode) {
        if (gameModeId == null) throw new BadRequestException("gameModeId is required");
        String st = normState(stateCode);
        if (st == null) throw new BadRequestException("stateCode is required");

        return sourceRepository.findByStateCodeInAndGameModeIdAndEnabledOrderByPriorityAsc(
                List.of(st, MULTI),
                gameModeId,
                true
        );
    }

    private static boolean supports(Source s, IngestionCapability cap) {
        return switch (cap) {
            case DRAW_LATEST -> s.isDrawLatest();
            case DRAW_BY_DATE -> s.isDrawByDate();
            case DRAW_HISTORY -> s.isDrawHistory();
            case GAME_LIST -> s.isSupportsGameList();
            case RULES -> s.isSupportsRules();
            case SCHEDULE -> s.isSupportsSchedule();
        };
    }

    private static String expandUrl(String template, LocalDate date) {
        if (template == null) return null;
        String out = template;
        if (date != null) out = out.replace("{date}", date.toString());
        out = out.replace("{limit}", "5000");
        return out;
    }

    private static String normState(String stateCode) {
        if (stateCode == null) return null;
        String s = stateCode.trim().toUpperCase(Locale.ROOT);
        return s.isBlank() ? null : s;
    }

    private static String acceptHeaderFor(SourceType type) {
        return switch (type) {
            case CSV -> "text/csv,text/plain;q=0.9,*/*;q=0.8";
            case PDF -> "application/pdf,application/octet-stream;q=0.9,*/*;q=0.8";
            case JSON -> "application/json,*/*;q=0.8";
            case HTML -> "text/html,*/*;q=0.8";
            case API -> "application/json,*/*;q=0.8";
        };
    }

    private static Map<String, Object> meta(Source src, DrawSourceClient.FetchedContent fetched) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (fetched != null) {
            m.put("contentType", fetched.contentType());
            m.put("finalUrl", fetched.finalUrl());
            m.put("statusCode", fetched.statusCode());
        }
        m.put("sourceType", src.getSourceType().name());
        m.put("parserKey", src.getParserKey());
        m.put("priority", src.getPriority());
        return m;
    }

    private static IngestionFailure.Attempt attempt(Source src, String url, DrawSourceClient.FetchedContent fetched, String errorMsg) {
        return IngestionFailure.Attempt.builder()
                .sourceId(src.getId())
                .priority(src.getPriority())
                .sourceType(src.getSourceType().name())
                .parserKey(src.getParserKey())
                .url(url)
                .finalUrl(fetched == null ? null : fetched.finalUrl())
                .statusCode(fetched == null ? null : fetched.statusCode())
                .contentType(fetched == null ? null : fetched.contentType())
                .errorMessage(errorMsg)
                .build();
    }

    private static IngestionFailureException ingestionFailure(
            String message,
            String capability,
            Long gameModeId,
            String stateCode,
            List<IngestionFailure.Attempt> attempts
    ) {
        IngestionFailureReason reason = inferReason(attempts);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("capability", capability);
        details.put("gameModeId", gameModeId);
        details.put("stateCode", normState(stateCode));
        details.put("attempts", attempts);

        return new IngestionFailureException(message, reason, reason.name(), details);
    }

    private static IngestionFailureReason inferReason(List<IngestionFailure.Attempt> attempts) {
        if (attempts == null || attempts.isEmpty()) {
            return IngestionFailureReason.MANUAL_ENTRY_REQUIRED;
        }

        for (IngestionFailure.Attempt a : attempts) {
            Integer status = a.getStatusCode();
            if (status != null && (status == 429 || status >= 500)) {
                return IngestionFailureReason.CHECK_BACK_LATER;
            }

            String msg = a.getErrorMessage();
            if (msg == null) continue;
            String m = msg.toLowerCase(Locale.ROOT);

            if (m.contains("timeout")
                    || m.contains("timed out")
                    || m.contains("connection")
                    || m.contains("download failed")
                    || m.contains("too many redirects")
                    || m.contains("only https")
                    || m.contains("host not allowed")) {
                return IngestionFailureReason.CHECK_BACK_LATER;
            }
        }

        return IngestionFailureReason.MANUAL_ENTRY_REQUIRED;
    }

    private static String safeMsg(Exception e) {
        if (e == null) return "unknown";
        String m = e.getMessage();
        return (m == null || m.isBlank()) ? e.getClass().getSimpleName() : m;
    }
}
