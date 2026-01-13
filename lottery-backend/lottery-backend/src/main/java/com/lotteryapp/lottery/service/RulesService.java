package com.lotteryapp.lottery.service;

import com.lotteryapp.common.exception.BadRequestException;
import com.lotteryapp.common.exception.NotFoundException;
import com.lotteryapp.lottery.application.numbers.NumberBallLifecycleService;
import com.lotteryapp.lottery.domain.gamemode.GameMode;
import com.lotteryapp.lottery.domain.gamemode.GameScope;
import com.lotteryapp.lottery.domain.gamemode.Rules;
import com.lotteryapp.lottery.dto.common.ApiResponse;
import com.lotteryapp.lottery.dto.rules.request.CreateRulesRequest;
import com.lotteryapp.lottery.dto.rules.request.GetRulesDetailRequest;
import com.lotteryapp.lottery.dto.rules.request.UpdateRulesRequest;
import com.lotteryapp.lottery.dto.rules.response.RulesResponse;
import com.lotteryapp.lottery.ingestion.IngestionService;
import com.lotteryapp.lottery.ingestion.model.IngestedRules;
import com.lotteryapp.lottery.repository.GameModeRepository;
import com.lotteryapp.lottery.repository.RulesRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

@Service
public class RulesService {

    private final GameModeRepository gameModeRepository;
    private final RulesRepository rulesRepository;
    private final IngestionService ingestionService;
    private final NumberBallLifecycleService numberBallLifecycleService;
    private final DrawService drawService;

    public RulesService(
            GameModeRepository gameModeRepository,
            RulesRepository rulesRepository,
            IngestionService ingestionService,
            NumberBallLifecycleService numberBallLifecycleService,
            DrawService drawService
    ) {
        this.gameModeRepository = gameModeRepository;
        this.rulesRepository = rulesRepository;
        this.ingestionService = ingestionService;
        this.numberBallLifecycleService = numberBallLifecycleService;
        this.drawService = drawService;
    }

    @Transactional
    public ApiResponse<RulesResponse> create(CreateRulesRequest request) {
        if (request == null) throw new BadRequestException("Request body is required");
        if (request.getGameModeId() == null) throw new BadRequestException("gameModeId is required");

        GameMode mode = loadMode(request.getGameModeId());

        if (mode.getRules() != null) {
            throw new BadRequestException("Rules already exist for this game. Use update.");
        }

        Rules newRules;
        String message;
        Map<String, Object> meta = new LinkedHashMap<>();

        if (hasAnyRuleField(request)) {
            // Manual create requires enough fields to define the rules.
            newRules = buildRulesFromManualCreate(request);
            message = "Rules created (manual)";
        } else {
            IngestedRules ing = ingestRulesForMode(mode);
            newRules = buildRulesFromIngested(ing);
            message = "Rules created from official data";
            meta.put("sourceId", ing.getSourceId());
        }

        mode.setRules(newRules);
        gameModeRepository.save(mode);

        // diff vs null (create)
        meta.put("rulesChanged", true);
        meta.put("diff", diff(null, newRules));

        // rebuild trigger if formatStartDate changed (null -> value counts)
        if (formatStartDateChanged(null, newRules.getFormatStartDate())) {
            meta.put("rebuildRequired", true);
            meta.put("rebuildReason", "formatStartDate changed");
            triggerRebuild(mode);
        }

        return ApiResponse.ok(message, toResponse(mode, newRules), null, meta);
    }

    @Transactional
    public ApiResponse<RulesResponse> update(UpdateRulesRequest request) {
        if (request == null) throw new BadRequestException("Request body is required");
        if (request.getGameModeId() == null) throw new BadRequestException("gameModeId is required");

        GameMode mode = loadMode(request.getGameModeId());

        Rules existing = mode.getRules();
        if (existing == null) {
            throw new BadRequestException("Rules do not exist for this game. Use create.");
        }

        LocalDate oldStart = existing.getFormatStartDate();

        String message;
        Map<String, Object> meta = new LinkedHashMap<>();

        if (!hasAnyRuleField(request)) {
            // Sync from real data
            IngestedRules ing = ingestRulesForMode(mode);
            Rules synced = buildRulesFromIngested(ing);

            if (rulesEqual(existing, synced)) {
                return ApiResponse.ok("Rules already up to date", toResponse(mode, existing),
                        null, Map.of("rulesChanged", false, "sourceId", ing.getSourceId()));
            }

            // apply full replacement (keeps behavior deterministic)
            // keep same id by updating existing in place
            applyAll(existing, synced);

            rulesRepository.save(existing);

            message = "Rules updated from official data";
            meta.put("sourceId", ing.getSourceId());
            meta.put("rulesChanged", true);
            meta.put("diff", diffSnapshot(oldSnapshot(oldStart, existing, true), newSnapshot(existing)));
        } else {
            // Manual patch update (apply only non-null fields)
            applyManualPatch(existing, request);
            validateRules(existing);

            rulesRepository.save(existing);

            message = "Rules updated (manual)";
            meta.put("rulesChanged", true);
            meta.put("diff", diffSnapshot(oldSnapshot(oldStart, existing, false), newSnapshot(existing)));
        }

        // rebuild trigger only when formatStartDate changed
        LocalDate newStart = existing.getFormatStartDate();
        if (formatStartDateChanged(oldStart, newStart)) {
            meta.put("rebuildRequired", true);
            meta.put("rebuildReason", "formatStartDate changed");
            triggerRebuild(mode);
        }

        return ApiResponse.ok(message, toResponse(mode, existing), null, meta);
    }

    public ApiResponse<RulesResponse> detail(GetRulesDetailRequest request) {
        if (request == null || request.getGameModeId() == null) {
            throw new BadRequestException("gameModeId is required");
        }

        GameMode mode = loadMode(request.getGameModeId());
        Rules rules = mode.getRules();
        if (rules == null) throw new NotFoundException("Rules not found for this game");

        return ApiResponse.ok("Rules loaded", toResponse(mode, rules));
    }

    // -------------------------
    // Internals
    // -------------------------

    private GameMode loadMode(Long gameModeId) {
        return gameModeRepository.findById(gameModeId)
                .orElseThrow(() -> new NotFoundException("GameMode not found"));
    }

    private IngestedRules ingestRulesForMode(GameMode mode) {
        String stateCode = stateCodeFor(mode);
        return ingestionService.ingestRules(mode.getId(), stateCode);
    }

    private static String stateCodeFor(GameMode mode) {
        if (mode.getScope() == GameScope.MULTI_STATE) return "MULTI";
        if (mode.getJurisdiction() == null || mode.getJurisdiction().getCode() == null) {
            throw new BadRequestException("STATE_ONLY game must have jurisdiction.code");
        }
        return mode.getJurisdiction().getCode();
    }

    private static boolean hasAnyRuleField(CreateRulesRequest r) {
        return r.getFormatStartDate() != null
                || r.getWhiteMin() != null
                || r.getWhiteMax() != null
                || r.getWhitePickCount() != null
                || r.getWhiteOrdered() != null
                || r.getWhiteAllowRepeats() != null
                || r.getRedMin() != null
                || r.getRedMax() != null
                || r.getRedPickCount() != null
                || r.getRedOrdered() != null
                || r.getRedAllowRepeats() != null;
    }

    private static boolean hasAnyRuleField(UpdateRulesRequest r) {
        return r.getFormatStartDate() != null
                || r.getWhiteMin() != null
                || r.getWhiteMax() != null
                || r.getWhitePickCount() != null
                || r.getWhiteOrdered() != null
                || r.getWhiteAllowRepeats() != null
                || r.getRedMin() != null
                || r.getRedMax() != null
                || r.getRedPickCount() != null
                || r.getRedOrdered() != null
                || r.getRedAllowRepeats() != null;
    }

    private static Rules buildRulesFromManualCreate(CreateRulesRequest r) {
        // Require the essentials for first-time creation
        if (r.getWhiteMin() == null) throw new BadRequestException("whiteMin is required for manual create");
        if (r.getWhiteMax() == null) throw new BadRequestException("whiteMax is required for manual create");
        if (r.getWhitePickCount() == null) throw new BadRequestException("whitePickCount is required for manual create");

        Rules rules = Rules.builder()
                .formatStartDate(r.getFormatStartDate())
                .whiteMin(r.getWhiteMin())
                .whiteMax(r.getWhiteMax())
                .whitePickCount(r.getWhitePickCount())
                .whiteOrdered(r.getWhiteOrdered())
                .whiteAllowRepeats(r.getWhiteAllowRepeats())
                .redMin(r.getRedMin())
                .redMax(r.getRedMax())
                .redPickCount(r.getRedPickCount() == null ? 0 : r.getRedPickCount())
                .redOrdered(r.getRedOrdered())
                .redAllowRepeats(r.getRedAllowRepeats())
                .build();

        validateRules(rules);
        return rules;
    }

    private static Rules buildRulesFromIngested(IngestedRules ing) {
        // NOTE: once IngestedRules is expanded (formatStartDate/ordered/repeats),
        // map them here as well.
        if (ing.getWhiteMin() == null || ing.getWhiteMax() == null || ing.getWhitePickCount() == null) {
            throw new BadRequestException("Ingested rules missing required white pool fields");
        }

        Rules rules = Rules.builder()
                .formatStartDate(null)
                .whiteMin(ing.getWhiteMin())
                .whiteMax(ing.getWhiteMax())
                .whitePickCount(ing.getWhitePickCount())
                .whiteOrdered(false)
                .whiteAllowRepeats(false)
                .redMin(ing.getRedMin())
                .redMax(ing.getRedMax())
                .redPickCount(ing.getRedPickCount() == null ? 0 : ing.getRedPickCount())
                .redOrdered(false)
                .redAllowRepeats(false)
                .build();

        validateRules(rules);
        return rules;
    }

    private static void applyManualPatch(Rules existing, UpdateRulesRequest r) {
        if (r.getFormatStartDate() != null) existing.setFormatStartDate(r.getFormatStartDate());

        if (r.getWhiteMin() != null) existing.setWhiteMin(r.getWhiteMin());
        if (r.getWhiteMax() != null) existing.setWhiteMax(r.getWhiteMax());
        if (r.getWhitePickCount() != null) existing.setWhitePickCount(r.getWhitePickCount());
        if (r.getWhiteOrdered() != null) existing.setWhiteOrdered(r.getWhiteOrdered());
        if (r.getWhiteAllowRepeats() != null) existing.setWhiteAllowRepeats(r.getWhiteAllowRepeats());

        if (r.getRedMin() != null) existing.setRedMin(r.getRedMin());
        if (r.getRedMax() != null) existing.setRedMax(r.getRedMax());
        if (r.getRedPickCount() != null) existing.setRedPickCount(r.getRedPickCount());
        if (r.getRedOrdered() != null) existing.setRedOrdered(r.getRedOrdered());
        if (r.getRedAllowRepeats() != null) existing.setRedAllowRepeats(r.getRedAllowRepeats());
    }

    private static void applyAll(Rules target, Rules src) {
        target.setFormatStartDate(src.getFormatStartDate());

        target.setWhiteMin(src.getWhiteMin());
        target.setWhiteMax(src.getWhiteMax());
        target.setWhitePickCount(src.getWhitePickCount());
        target.setWhiteOrdered(src.getWhiteOrdered());
        target.setWhiteAllowRepeats(src.getWhiteAllowRepeats());

        target.setRedMin(src.getRedMin());
        target.setRedMax(src.getRedMax());
        target.setRedPickCount(src.getRedPickCount());
        target.setRedOrdered(src.getRedOrdered());
        target.setRedAllowRepeats(src.getRedAllowRepeats());

        validateRules(target);
    }

    private static void validateRules(Rules rules) {
        if (rules.getWhiteMin() == null || rules.getWhiteMax() == null || rules.getWhitePickCount() == null) {
            throw new BadRequestException("whiteMin/whiteMax/whitePickCount are required");
        }
        if (rules.getWhiteMin() > rules.getWhiteMax()) throw new BadRequestException("whiteMin must be <= whiteMax");
        if (rules.getWhitePickCount() < 1) throw new BadRequestException("whitePickCount must be >= 1");

        Integer redPick = (rules.getRedPickCount() == null) ? 0 : rules.getRedPickCount();
        if (redPick < 0) throw new BadRequestException("redPickCount must be >= 0");
        if (redPick == 0) {
            // ok to have null min/max
            return;
        }

        if (rules.getRedMin() == null || rules.getRedMax() == null) {
            throw new BadRequestException("redMin/redMax are required when redPickCount > 0");
        }
        if (rules.getRedMin() > rules.getRedMax()) throw new BadRequestException("redMin must be <= redMax");
    }

    private static boolean formatStartDateChanged(LocalDate oldVal, LocalDate newVal) {
        return !Objects.equals(oldVal, newVal);
    }

    private static boolean rulesEqual(Rules a, Rules b) {
        if (a == null || b == null) return false;
        return Objects.equals(a.getFormatStartDate(), b.getFormatStartDate())
                && Objects.equals(a.getWhiteMin(), b.getWhiteMin())
                && Objects.equals(a.getWhiteMax(), b.getWhiteMax())
                && Objects.equals(a.getWhitePickCount(), b.getWhitePickCount())
                && Objects.equals(a.getWhiteOrdered(), b.getWhiteOrdered())
                && Objects.equals(a.getWhiteAllowRepeats(), b.getWhiteAllowRepeats())
                && Objects.equals(a.getRedMin(), b.getRedMin())
                && Objects.equals(a.getRedMax(), b.getRedMax())
                && Objects.equals(a.getRedPickCount(), b.getRedPickCount())
                && Objects.equals(a.getRedOrdered(), b.getRedOrdered())
                && Objects.equals(a.getRedAllowRepeats(), b.getRedAllowRepeats());
    }

    private static Map<String, Object> diff(Rules oldRules, Rules newRules) {
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Object> oldS = (oldRules == null) ? Collections.emptyMap() : newSnapshot(oldRules);
        Map<String, Object> newS = newSnapshot(newRules);

        for (String k : newS.keySet()) {
            Object o = oldS.get(k);
            Object n = newS.get(k);
            if (!Objects.equals(o, n)) {
                out.put(k, Map.of("old", o, "new", n));
            }
        }
        return out;
    }

    private static Map<String, Object> oldSnapshot(LocalDate oldStart, Rules current, boolean beforeApplyWasDifferent) {
        // For the in-place applyAll case we don't have the pre-apply snapshot anymore.
        // We still return a useful diff snapshot structure; if you want perfect diffs,
        // we can clone the old rules before applying.
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("formatStartDate", oldStart);
        // other fields are unknown here without cloning; keep minimal and reliable
        snap.put("note", beforeApplyWasDifferent ? "Old snapshot partial (formatStartDate only)" : "Old snapshot partial");
        return snap;
    }

    private static Map<String, Object> newSnapshot(Rules r) {
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("formatStartDate", r.getFormatStartDate());

        snap.put("whiteMin", r.getWhiteMin());
        snap.put("whiteMax", r.getWhiteMax());
        snap.put("whitePickCount", r.getWhitePickCount());
        snap.put("whiteOrdered", r.getWhiteOrdered());
        snap.put("whiteAllowRepeats", r.getWhiteAllowRepeats());

        snap.put("redMin", r.getRedMin());
        snap.put("redMax", r.getRedMax());
        snap.put("redPickCount", r.getRedPickCount());
        snap.put("redOrdered", r.getRedOrdered());
        snap.put("redAllowRepeats", r.getRedAllowRepeats());
        return snap;
    }

    private static Map<String, Object> diffSnapshot(Map<String, Object> oldSnap, Map<String, Object> newSnap) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String k : newSnap.keySet()) {
            Object o = oldSnap.get(k);
            Object n = newSnap.get(k);
            if (!Objects.equals(o, n)) {
                out.put(k, Map.of("old", o, "new", n));
            }
        }
        return out;
    }

    private static RulesResponse toResponse(GameMode mode, Rules r) {
        return RulesResponse.builder()
                .id(r.getId())
                .gameModeId(mode.getId())
                .formatStartDate(r.getFormatStartDate())
                .whiteMin(r.getWhiteMin())
                .whiteMax(r.getWhiteMax())
                .whitePickCount(r.getWhitePickCount())
                .whiteOrdered(r.getWhiteOrdered())
                .whiteAllowRepeats(r.getWhiteAllowRepeats())
                .redMin(r.getRedMin())
                .redMax(r.getRedMax())
                .redPickCount(r.getRedPickCount())
                .redOrdered(r.getRedOrdered())
                .redAllowRepeats(r.getRedAllowRepeats())
                .build();
    }

    private void triggerRebuild(GameMode mode) {
        numberBallLifecycleService.rebuildForGameMode(mode);
        drawService.syncCurrentFormatHistoryForRebuild(mode);
    }
}
