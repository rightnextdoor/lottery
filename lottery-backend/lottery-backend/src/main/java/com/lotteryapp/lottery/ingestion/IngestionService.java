package com.lotteryapp.lottery.ingestion;

import com.lotteryapp.common.exception.BadRequestException;
import com.lotteryapp.common.exception.IngestionFailureException;
import com.lotteryapp.common.exception.IngestionFailureReason;
import com.lotteryapp.common.exception.NotFoundException;
import com.lotteryapp.lottery.domain.source.Source;
import com.lotteryapp.lottery.domain.source.SourceType;
import com.lotteryapp.lottery.ingestion.model.*;
import com.lotteryapp.lottery.ingestion.source.DrawSourceClient;
import com.lotteryapp.lottery.parser.*;
import com.lotteryapp.lottery.repository.SourceRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class IngestionService {

    private static final String MULTI = "MULTI";

    private final SourceRepository sourceRepository;
    private final DrawSourceClient drawSourceClient;

    private final DrawParserRegistry drawParserRegistry;
    private final RulesParserRegistry rulesParserRegistry;
    private final ScheduleParserRegistry scheduleParserRegistry;
    private final GameListParserRegistry gameListParserRegistry;

    public IngestionService(
            SourceRepository sourceRepository,
            DrawSourceClient drawSourceClient,
            DrawParserRegistry drawParserRegistry,
            RulesParserRegistry rulesParserRegistry,
            ScheduleParserRegistry scheduleParserRegistry,
            GameListParserRegistry gameListParserRegistry
    ) {
        this.sourceRepository = sourceRepository;
        this.drawSourceClient = drawSourceClient;
        this.drawParserRegistry = drawParserRegistry;
        this.rulesParserRegistry = rulesParserRegistry;
        this.scheduleParserRegistry = scheduleParserRegistry;
        this.gameListParserRegistry = gameListParserRegistry;
    }

    // -----------------------------
    // DRAWS
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

                RulesParser parser = rulesParserRegistry.resolve(src.getSourceType(), src.getParserKey());
                IngestedRules rules = parser.parse(fetched.bytes(), gameModeId, normState(stateCode));

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

                ScheduleParser parser = scheduleParserRegistry.resolve(src.getSourceType(), src.getParserKey());
                IngestedSchedule sched = parser.parse(fetched.bytes(), gameModeId, normState(stateCode));

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

        // We don't have a gameModeId yet, so we cannot use loadSources(...).
        // Instead, we scan enabled sources for stateCode + MULTI and filter to supportsGameList.
        List<Source> sources = sourceRepository.findAll().stream()
                .filter(Source::isEnabled)
                .filter(s -> {
                    String sc = s.getStateCode();
                    if (sc == null) return false;
                    String norm = sc.trim().toUpperCase(Locale.ROOT);
                    return norm.equals(st) || norm.equals(MULTI);
                })
                .filter(Source::isSupportsGameList)
                .sorted(Comparator.comparingInt(Source::getPriority))
                .collect(Collectors.toList());

        List<IngestionFailure.Attempt> attempts = new ArrayList<>();

        for (Source src : sources) {
            String url = expandUrl(src.getUrlTemplate(), null);

            try {
                DrawSourceClient.FetchedContent fetched = drawSourceClient.fetch(url, Map.of(
                        "Accept", acceptHeaderFor(src.getSourceType())
                ));

                GameListParser parser = gameListParserRegistry.resolve(src.getSourceType(), src.getParserKey());
                IngestedGameList list = parser.parse(fetched.bytes(), st);

                list.setStateCode(st);
                list.setSourceId(src.getId());
                list.setFetchedAt(Instant.now());
                list.setMeta(meta(src, fetched));

                return list;

            } catch (Exception e) {
                attempts.add(attempt(src, url, null, safeMsg(e)));
            }
        }

        throw new IngestionFailureException(
                "All game list sources failed",
                IngestionFailureReason.MANUAL_ENTRY_REQUIRED,
                IngestionFailureReason.MANUAL_ENTRY_REQUIRED.name(),
                buildFailureDetails(IngestionCapability.GAME_LIST.name(), null, st, attempts)
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
            case CSV -> "text/csv,*/*;q=0.8";
            case PDF -> "application/pdf,*/*;q=0.8";
            case JSON, API -> "application/json,*/*;q=0.8";
            case HTML -> "text/html,*/*;q=0.8";
        };
    }

    private static Map<String, Object> meta(Source src, DrawSourceClient.FetchedContent fetched) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sourceType", src.getSourceType().name());
        m.put("parserKey", src.getParserKey());
        m.put("url", src.getUrlTemplate());
        m.put("finalUrl", fetched.finalUrl());
        m.put("statusCode", fetched.statusCode());
        m.put("contentType", fetched.contentType());
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


    private static java.util.Map<String, Object> buildFailureDetails(
            String capability,
            Long gameModeId,
            String stateCode,
            java.util.List<IngestionFailure.Attempt> attempts
    ) {
        java.util.Map<String, Object> details = new java.util.LinkedHashMap<>();
        if (capability != null && !capability.isBlank()) details.put("capability", capability);
        if (gameModeId != null) details.put("gameModeId", gameModeId);
        String st = normState(stateCode);
        if (st != null) details.put("stateCode", st);
        if (attempts != null && !attempts.isEmpty()) details.put("attempts", attempts);
        return details;
    }

    private static IngestionFailureException ingestionFailure(
            String message,
            String capability,
            Long gameModeId,
            String stateCode,
            List<IngestionFailure.Attempt> attempts
    ) {
        return new IngestionFailureException(
                message,
                IngestionFailureReason.CHECK_BACK_LATER,
                IngestionFailureReason.CHECK_BACK_LATER.name(),
                buildFailureDetails(capability, gameModeId, stateCode, attempts)
        );
    }

    private static String safeMsg(Exception e) {
        if (e == null) return null;
        String msg = e.getMessage();
        if (msg == null || msg.isBlank()) return e.getClass().getSimpleName();
        return msg.length() > 300 ? msg.substring(0, 300) : msg;
    }
}
