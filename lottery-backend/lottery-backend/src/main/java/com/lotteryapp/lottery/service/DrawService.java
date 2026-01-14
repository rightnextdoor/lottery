package com.lotteryapp.lottery.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lotteryapp.common.exception.BadRequestException;
import com.lotteryapp.common.exception.NotFoundException;
import com.lotteryapp.lottery.application.numbers.NumberBallLifecycleService;
import com.lotteryapp.lottery.domain.draw.*;
import com.lotteryapp.lottery.domain.gamemode.DrawDay;
import com.lotteryapp.lottery.domain.gamemode.GameMode;
import com.lotteryapp.lottery.domain.gamemode.GameModeStatus;
import com.lotteryapp.lottery.domain.numbers.NumberBall;
import com.lotteryapp.lottery.dto.common.ApiResponse;
import com.lotteryapp.lottery.dto.common.PageResponse;
import com.lotteryapp.lottery.dto.draw.request.*;
import com.lotteryapp.lottery.dto.draw.response.*;
import com.lotteryapp.lottery.dto.gamemode.response.GameModeResponse;
import com.lotteryapp.lottery.ingestion.IngestionService;
import com.lotteryapp.lottery.ingestion.model.IngestedDraw;
import com.lotteryapp.lottery.repository.DrawConflictRepository;
import com.lotteryapp.lottery.repository.DrawResultRepository;
import com.lotteryapp.lottery.repository.GameModeRepository;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;


import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DrawService {

    private static final int LAST_DRAWS_COUNT = 5;
    private static final int SAFE_BACKFILL_MAX_DATES = 20;

    private final GameModeRepository gameModeRepository;
    private final DrawResultRepository drawResultRepository;
    private final DrawConflictRepository drawConflictRepository;
    private final IngestionService ingestionService;

    private final NumberBallService numberBallService;
    private final NumberBallLifecycleService numberBallLifecycleService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public DrawService(
            GameModeRepository gameModeRepository,
            DrawResultRepository drawResultRepository,
            DrawConflictRepository drawConflictRepository,
            IngestionService ingestionService,
            NumberBallService numberBallService,
            NumberBallLifecycleService numberBallLifecycleService
    ) {
        this.gameModeRepository = gameModeRepository;
        this.drawResultRepository = drawResultRepository;
        this.drawConflictRepository = drawConflictRepository;
        this.ingestionService = ingestionService;
        this.numberBallService = numberBallService;
        this.numberBallLifecycleService = numberBallLifecycleService;
    }

    @Transactional
    public ApiResponse<DrawBundleResponse> getLatest(GetLatestDrawRequest request) {
        GameMode mode = requireMode(request.getGameModeId());
        ensureDrawsUpToDate(mode, request.getStateCode());

        DrawResult latest = drawResultRepository.findTopByGameModeIdOrderByDrawDateDesc(mode.getId())
                .orElseGet(() -> saveOfficialFromIngestion(mode, ingestionService.ingestLatestDraw(mode.getId(), request.getStateCode())));

        DrawBundleResponse bundle = DrawBundleResponse.builder()
                .gameMode(toGameModeResponse(mode))
                .draw(toDrawResponse(latest))
                .build();

        return ApiResponse.ok("Latest draw loaded", bundle);
    }

    @Transactional
    public ApiResponse<DrawBundleResponse> getLast5(GetLastDrawsRequest request) {
        GameMode mode = requireMode(request.getGameModeId());
        ensureDrawsUpToDate(mode, request.getStateCode());

        List<DrawResult> draws = drawResultRepository.findByGameModeIdOrderByDrawDateDesc(
                mode.getId(),
                PageRequest.of(0, LAST_DRAWS_COUNT)
        );

        DrawBundleResponse bundle = DrawBundleResponse.builder()
                .gameMode(toGameModeResponse(mode))
                .draws(draws.stream().map(this::toDrawResponse).toList())
                .build();

        return ApiResponse.ok("Last 5 draws loaded", bundle);
    }

    @Transactional
    public ApiResponse<DrawBundleResponse> getByDate(GetDrawByDateRequest request) {
        GameMode mode = requireMode(request.getGameModeId());
        ensureDrawsUpToDate(mode, request.getStateCode());

        DrawResult draw = drawResultRepository.findByGameModeIdAndDrawDate(mode.getId(), request.getDrawDate())
                .orElseGet(() -> saveOfficialFromIngestion(mode,
                        ingestionService.ingestDrawByDate(mode.getId(), request.getStateCode(), request.getDrawDate())
                ));

        DrawBundleResponse bundle = DrawBundleResponse.builder()
                .gameMode(toGameModeResponse(mode))
                .draw(toDrawResponse(draw))
                .build();

        return ApiResponse.ok("Draw loaded", bundle);
    }

    @Transactional
    public ApiResponse<DrawBundleResponse> getCurrentFormat(GetCurrentFormatDrawsRequest request) {
        GameMode mode = requireMode(request.getGameModeId());
        if (mode.getRules() == null || mode.getRules().getFormatStartDate() == null) {
            throw new BadRequestException("Rules.formatStartDate is required for current format draws");
        }

        ensureDrawsUpToDate(mode, request.getStateCode());

        LocalDate start = mode.getRules().getFormatStartDate();
        LocalDate end = computeLatestExpectedDrawDate(mode);

        // For initial history, we ingest history if DB empty in this range.
        List<DrawResult> existing = drawResultRepository
                .findByGameModeIdAndDrawDateBetweenOrderByDrawDateAsc(mode.getId(), start, end);

        if (existing.isEmpty()) {
            List<IngestedDraw> history = ingestionService.ingestDrawHistory(mode.getId(), request.getStateCode());

            List<DrawResult> updatedDraws = new ArrayList<>();

            for (IngestedDraw d : history) {
                DrawResult saved = saveOfficialFromIngestion(mode, d);

                if (saved != null && saved.getOrigin() == DrawOrigin.OFFICIAL) {
                    updatedDraws.add(saved);
                }
            }

            if (!updatedDraws.isEmpty()) {
                onDrawActiveForNumberBalls(mode, updatedDraws);
            }

            existing = drawResultRepository
                    .findByGameModeIdAndDrawDateBetweenOrderByDrawDateAsc(mode.getId(), start, end);
        }

        DrawBundleResponse bundle = DrawBundleResponse.builder()
                .gameMode(toGameModeResponse(mode))
                .draws(existing.stream().map(this::toDrawResponse).toList())
                .build();

        return ApiResponse.ok("Current format draws loaded", bundle);
    }

    @Transactional
    public DrawResponse getWinningNumbersForCheck(Long gameModeId, String stateCode, LocalDate drawDate) {
        if (gameModeId == null) throw new BadRequestException("gameModeId is required");
        if (stateCode == null || stateCode.isBlank()) throw new BadRequestException("stateCode is required");

        GameMode mode = requireMode(gameModeId);
        ensureDrawsUpToDate(mode, stateCode);

        DrawResult draw;
        if (drawDate == null) {
            draw = drawResultRepository.findTopByGameModeIdOrderByDrawDateDesc(mode.getId())
                    .orElseGet(() -> saveOfficialFromIngestion(mode, ingestionService.ingestLatestDraw(mode.getId(), stateCode)));
        } else {
            draw = drawResultRepository.findByGameModeIdAndDrawDate(mode.getId(), drawDate)
                    .orElseGet(() -> saveOfficialFromIngestion(mode,
                            ingestionService.ingestDrawByDate(mode.getId(), stateCode, drawDate)
                    ));
        }

        return toDrawResponse(draw);
    }


    public void syncCurrentFormatHistoryForRebuild(GameMode mode) {
        if (mode == null || mode.getId() == null) {
            throw new BadRequestException("GameMode is required");
        }
        if (mode.getRules() == null || mode.getRules().getFormatStartDate() == null) {
            throw new BadRequestException("Rules.formatStartDate is required for rebuild draw sync");
        }
        if (mode.getJurisdiction() == null || mode.getJurisdiction().getCode() == null) {
            throw new BadRequestException("GameMode jurisdiction code (stateCode) is required for rebuild draw sync");
        }

        String stateCode = mode.getJurisdiction().getCode();
        LocalDate start = mode.getRules().getFormatStartDate();
        LocalDate end = LocalDate.now();

        List<IngestedDraw> ingestedHistory = ingestionService.ingestDrawHistory(mode.getId(), stateCode);
        for (IngestedDraw ingested : ingestedHistory) {
            if (ingested == null || ingested.getDrawDate() == null) continue;
            if (ingested.getDrawDate().isBefore(start) || ingested.getDrawDate().isAfter(end)) continue;

            saveOfficialFromIngestion(mode, ingested);
        }

        List<DrawResult> draws = drawResultRepository
                .findByGameModeIdAndDrawDateBetweenOrderByDrawDateAsc(mode.getId(), start, end);

        onDrawActiveForNumberBalls(mode, draws);
    }

    public ApiResponse<DrawScheduleResponse> getSchedule(GetDrawScheduleRequest request) {
        GameMode mode = requireMode(request.getGameModeId());

        DrawScheduleResponse resp = DrawScheduleResponse.builder()
                .drawDays(mode.getDrawDays())
                .nextDrawDate(mode.getNextDrawDate())
                .drawTimeLocal(mode.getDrawTimeLocal())
                .drawTimeZoneId(mode.getDrawTimeZoneId())
                .build();

        return ApiResponse.ok("Schedule loaded", resp);
    }

    @Transactional
    public ApiResponse<DrawSyncStatusResponse> getSyncStatus(GetDrawSyncStatusRequest request) {
        GameMode mode = requireMode(request.getGameModeId());

        LocalDate latestStored = drawResultRepository.findTopByGameModeIdOrderByDrawDateDesc(mode.getId())
                .map(DrawResult::getDrawDate)
                .orElse(null);

        LocalDate latestExpected = computeLatestExpectedDrawDate(mode);

        int missing = estimateMissingDrawCount(mode, latestStored, latestExpected);
        long conflicts = drawConflictRepository.countByGameModeId(mode.getId());

        GameModeStatus status = (missing == 0) ? GameModeStatus.UP_TO_DATE : GameModeStatus.OUT_OF_DATE;

        DrawSyncStatusResponse resp = DrawSyncStatusResponse.builder()
                .status(status)
                .latestStoredDrawDate(latestStored)
                .latestExpectedDrawDate(latestExpected)
                .missingDrawCountEstimate(missing)
                .conflictCount(conflicts)
                .build();

        return ApiResponse.ok("Draw sync status loaded", resp);
    }

    // -----------------------------
    // UPSERT (manual vs real-data)
    // -----------------------------

    @Transactional
    public ApiResponse<DrawBundleResponse> upsert(UpsertDrawRequest request) {
        GameMode mode = requireMode(request.getGameModeId());

        boolean manual = isManualRequest(request);

        if (!manual) {
            // Real data mode: ingest date or latest
            IngestedDraw ingested = (request.getDrawDate() == null)
                    ? ingestionService.ingestLatestDraw(mode.getId(), request.getStateCode())
                    : ingestionService.ingestDrawByDate(mode.getId(), request.getStateCode(), request.getDrawDate());

            DrawResult saved = saveOfficialFromIngestion(mode, ingested);

            if (saved != null && saved.getOrigin() == DrawOrigin.OFFICIAL) {
                onDrawActiveForNumberBalls(mode, List.of(saved));
            }

            DrawBundleResponse bundle = DrawBundleResponse.builder()
                    .gameMode(toGameModeResponse(mode))
                    .draw(toDrawResponse(saved))
                    .build();

            return ApiResponse.ok("Official draw ingested", bundle);
        }

        // Manual mode requires drawDate + at least one pool
        if (request.getDrawDate() == null) throw new BadRequestException("drawDate is required for manual upsert");
        if ((request.getWhiteNumbers() == null || request.getWhiteNumbers().isEmpty())
                && (request.getRedNumbers() == null || request.getRedNumbers().isEmpty())) {
            throw new BadRequestException("whiteNumbers and/or redNumbers is required for manual upsert");
        }

        // Validate numbers vs Rules
        validateNumbers(mode, request.getWhiteNumbers(), request.getRedNumbers());

        Optional<DrawResult> existingOpt = drawResultRepository.findByGameModeIdAndDrawDate(mode.getId(), request.getDrawDate());
        DrawResult draw = existingOpt.orElseGet(() -> DrawResult.builder()
                .gameMode(mode)
                .drawDate(request.getDrawDate())
                .origin(DrawOrigin.MANUAL)
                .build());

        DrawOrigin priorOrigin = existingOpt.map(DrawResult::getOrigin).orElse(null);
        List<Integer> priorOfficialW = null;
        List<Integer> priorOfficialR = null;

        if (priorOrigin == DrawOrigin.OFFICIAL) {
            // Capture official numbers BEFORE we mutate picks.
            priorOfficialW = picksToList(draw, "WHITE");
            priorOfficialR = picksToList(draw, "RED");
        }

        boolean differsFromPriorOfficial = (priorOrigin == DrawOrigin.OFFICIAL)
                && !sameNumbers(priorOfficialW, priorOfficialR, request.getWhiteNumbers(), request.getRedNumbers());

        if (differsFromPriorOfficial) {
            draw.setOrigin(DrawOrigin.MANUAL);
            draw.getPicks().clear();
            addPicks(draw, request.getWhiteNumbers(), request.getRedNumbers());
        } else if (priorOrigin == DrawOrigin.OFFICIAL) {
            // Manual numbers match official: keep OFFICIAL active and do not overwrite picks.
        } else {
            draw.setOrigin(DrawOrigin.MANUAL);
            draw.getPicks().clear();
            addPicks(draw, request.getWhiteNumbers(), request.getRedNumbers());
        }

        DrawResult saved = drawResultRepository.save(draw);

        onDrawActiveForNumberBalls(mode, List.of(saved));

        createOrUpdateConflictIfOfficialDiffers(
                mode,
                saved,
                priorOrigin,
                priorOfficialW,
                priorOfficialR,
                request.getWhiteNumbers(),
                request.getRedNumbers()
        );

        updateGameModeLatestSnapshotIfLatest(mode, saved, request);

        DrawBundleResponse bundle = DrawBundleResponse.builder()
                .gameMode(toGameModeResponse(mode))
                .draw(toDrawResponse(saved))
                .build();

        return ApiResponse.ok("Manual draw saved", bundle);
    }


    // -----------------------------
    // CONFLICTS
    // -----------------------------

    public PageResponse<DrawConflictResponse> listConflicts(ListDrawConflictsRequest request) {
        if (request == null) throw new BadRequestException("Request body is required");

        int pageNumber = request.getPageNumber() == null ? 0 : request.getPageNumber();
        int pageSize = request.getPageSize() == null ? 25 : request.getPageSize();

        Pageable pageable = PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Order.desc("drawDate")));

        Page<DrawConflict> page = drawConflictRepository.findByGameModeIdOrderByDrawDateDesc(request.getGameModeId(), pageable);

        List<DrawConflictResponse> items = page.getContent().stream()
                .map(this::toConflictResponse)
                .toList();

        PageResponse.PageMeta meta = PageResponse.PageMeta.builder()
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .hasNext(page.hasNext())
                .hasPrevious(page.hasPrevious())
                .sort("drawDate,desc")
                .build();

        return PageResponse.<DrawConflictResponse>builder()
                .message("Conflicts loaded")
                .items(items)
                .meta(meta)
                .build();
    }

    @Transactional
    public ApiResponse<Void> acknowledgeConflict(AcknowledgeDrawConflictRequest request) {
        if (request == null || request.getConflictId() == null) {
            throw new BadRequestException("conflictId is required");
        }

        DrawConflict conflict = drawConflictRepository.findById(request.getConflictId())
                .orElseThrow(() -> new NotFoundException("Conflict not found"));

        conflict.setAcknowledged(true);
        drawConflictRepository.save(conflict);

        return ApiResponse.ok("Conflict acknowledged", null);
    }

    @Transactional
    public ApiResponse<Void> resolveConflict(ResolveDrawConflictRequest request) {
        if (request == null || request.getConflictId() == null || request.getResolution() == null) {
            throw new BadRequestException("conflictId and resolution are required");
        }

        DrawConflict conflict = drawConflictRepository.findById(request.getConflictId())
                .orElseThrow(() -> new NotFoundException("Conflict not found"));

        DrawResult draw = conflict.getDrawResult();

        List<Integer> manualW = readIntList(conflict.getManualWhiteJson());
        List<Integer> manualR = readIntList(conflict.getManualRedJson());
        List<Integer> officialW = readIntList(conflict.getOfficialWhiteJson());
        List<Integer> officialR = readIntList(conflict.getOfficialRedJson());

        if (request.getResolution() == ResolveDrawConflictRequest.Resolution.OFFICIAL) {
            draw.setOrigin(DrawOrigin.OFFICIAL);
            draw.getPicks().clear();
            addPicks(draw, officialW, officialR);

            DrawResult saved = drawResultRepository.save(draw);

            onDrawActiveForNumberBalls(saved.getGameMode(), List.of(saved));

            updateGameModeWinningSnapshotIfLatest(saved.getGameMode(), saved, officialW, officialR);
        } else {
            draw.setOrigin(DrawOrigin.MANUAL);

            DrawResult saved = drawResultRepository.save(draw);

            onDrawActiveForNumberBalls(saved.getGameMode(), List.of(saved));

            updateGameModeWinningSnapshotIfLatest(saved.getGameMode(), saved, manualW, manualR);
        }

        // Hard delete conflict row after resolution
        drawConflictRepository.delete(conflict);

        return ApiResponse.ok("Conflict resolved", null);
    }


    // -----------------------------
    // ensure up-to-date
    // -----------------------------

    private void ensureDrawsUpToDate(GameMode mode, String stateCode) {
        LocalDate latestExpected = computeLatestExpectedDrawDate(mode);

        LocalDate latestStored = drawResultRepository.findTopByGameModeIdOrderByDrawDateDesc(mode.getId())
                .map(DrawResult::getDrawDate)
                .orElse(null);

        int missing = estimateMissingDrawCount(mode, latestStored, latestExpected);

        if (missing == 0) {
            if (mode.getStatus() != GameModeStatus.UP_TO_DATE) {
                mode.setStatus(GameModeStatus.UP_TO_DATE);
                gameModeRepository.save(mode);
            }
            return;
        }

        mode.setStatus(GameModeStatus.OUT_OF_DATE);
        gameModeRepository.save(mode);

        // ingest missing dates, safe limit
        List<LocalDate> missingDates = computeMissingDrawDates(mode, latestStored, latestExpected)
                .stream()
                .limit(SAFE_BACKFILL_MAX_DATES)
                .toList();

        List<DrawResult> updatedDraws = new ArrayList<>();

        for (LocalDate d : missingDates) {
            IngestedDraw ingested = ingestionService.ingestDrawByDate(mode.getId(), stateCode, d);

            DrawResult saved = saveOfficialFromIngestion(mode, ingested);

            if (saved != null && saved.getOrigin() == DrawOrigin.OFFICIAL) {
                updatedDraws.add(saved);
            }
        }

        if (!updatedDraws.isEmpty()) {
            onDrawActiveForNumberBalls(mode, updatedDraws);
        }

        // after ingest, recompute status
        LocalDate newest = drawResultRepository.findTopByGameModeIdOrderByDrawDateDesc(mode.getId())
                .map(DrawResult::getDrawDate)
                .orElse(null);

        int remaining = estimateMissingDrawCount(mode, newest, latestExpected);
        mode.setStatus(remaining == 0 ? GameModeStatus.UP_TO_DATE : GameModeStatus.OUT_OF_DATE);

        // update next draw date on refresh
        mode.setNextDrawDate(computeNextDrawDate(mode));

        gameModeRepository.save(mode);
    }

    // -----------------------------
    // save / map helpers
    // -----------------------------

    private DrawResult saveOfficialFromIngestion(GameMode mode, IngestedDraw ingested) {
        if (ingested == null || ingested.getDrawDate() == null) {
            throw new BadRequestException("Ingested draw missing drawDate");
        }

        DrawResult draw = drawResultRepository.findByGameModeIdAndDrawDate(mode.getId(), ingested.getDrawDate())
                .orElseGet(() -> DrawResult.builder()
                        .gameMode(mode)
                        .drawDate(ingested.getDrawDate())
                        .build());

        // if there is an active manual draw and official differs, store official snapshot as conflict instead of overwriting
        if (draw.getId() != null && draw.getOrigin() == DrawOrigin.MANUAL) {
            List<Integer> manualW = picksToList(draw, "WHITE");
            List<Integer> manualR = picksToList(draw, "RED");

            if (!sameNumbers(manualW, manualR, ingested.getWhiteNumbers(), ingested.getRedNumbers())) {
                createOrUpdateConflict(mode, draw, manualW, manualR, ingested.getWhiteNumbers(), ingested.getRedNumbers());
                return draw; // keep manual active
            }
        }

        // otherwise write official into draw result
        draw.setOrigin(DrawOrigin.OFFICIAL);
        draw.getPicks().clear();
        addPicks(draw, ingested.getWhiteNumbers(), ingested.getRedNumbers());

        boolean isNewOfficialDraw = (draw.getId() == null);

        DrawResult saved = drawResultRepository.save(draw);

        // update GameMode snapshot if newest
        updateGameModeLatestSnapshotIfLatest(mode, saved, ingested);

        return saved;
    }

    private void updateGameModeLatestSnapshotIfLatest(GameMode mode, DrawResult draw, IngestedDraw ingested) {
        if (isLatest(mode, draw.getDrawDate())) {
            mode.setLatestDrawDate(draw.getDrawDate());
            mode.setLatestWhiteWinningCsv(csv(ingested.getWhiteNumbers()));
            mode.setLatestRedWinningCsv(csv(ingested.getRedNumbers()));

            // store jackpot/cash/time/tz ONLY on GameMode
            mode.setLatestJackpotAmount(toBigDecimal(ingested.getJackpotAmount()));
            mode.setLatestCashValue(toBigDecimal(ingested.getCashValue()));

            // drawTimeLocal/drawTimeZoneId could come from schedule ingestion later; keep as-is here
            gameModeRepository.save(mode);
        }
    }

    private void updateGameModeLatestSnapshotIfLatest(GameMode mode, DrawResult draw, UpsertDrawRequest req) {
        if (isLatest(mode, draw.getDrawDate())) {
            mode.setLatestDrawDate(draw.getDrawDate());
            mode.setLatestWhiteWinningCsv(csv(req.getWhiteNumbers()));
            mode.setLatestRedWinningCsv(csv(req.getRedNumbers()));

            if (req.getJackpotAmount() != null) mode.setLatestJackpotAmount(req.getJackpotAmount());
            if (req.getCashValue() != null) mode.setLatestCashValue(req.getCashValue());
            if (req.getDrawTimeLocal() != null) mode.setDrawTimeLocal(req.getDrawTimeLocal());
            if (req.getDrawTimeZoneId() != null) mode.setDrawTimeZoneId(req.getDrawTimeZoneId());

            gameModeRepository.save(mode);
        }
    }

    private void updateGameModeWinningSnapshotIfLatest(GameMode mode, DrawResult draw, List<Integer> white, List<Integer> red) {
        if (isLatest(mode, draw.getDrawDate())) {
            mode.setLatestDrawDate(draw.getDrawDate());
            mode.setLatestWhiteWinningCsv(csv(white));
            mode.setLatestRedWinningCsv(csv(red));
            gameModeRepository.save(mode);
        }
    }

    private void createOrUpdateConflictIfOfficialDiffers(
            GameMode mode,
            DrawResult savedDraw,
            DrawOrigin priorOrigin,
            List<Integer> priorOfficialW,
            List<Integer> priorOfficialR,
            List<Integer> manualW,
            List<Integer> manualR
    ) {
        if (savedDraw.getId() == null) return;

        Optional<DrawConflict> existingConflictOpt = drawConflictRepository.findByDrawResultId(savedDraw.getId());

        // Case A: Manual entry happened AFTER we already had OFFICIAL data for this date.
        // If numbers differ, create/update conflict (stores BOTH sets) and keep MANUAL active.
        if (priorOrigin == DrawOrigin.OFFICIAL && priorOfficialW != null && priorOfficialR != null) {
            if (!sameNumbers(manualW, manualR, priorOfficialW, priorOfficialR)) {
                createOrUpdateConflict(mode, savedDraw, manualW, manualR, priorOfficialW, priorOfficialR);
            } else {
                // If a conflict somehow exists but numbers now match, remove the conflict.
                existingConflictOpt.ifPresent(drawConflictRepository::delete);
            }
            return;
        }

        // Case B: Manual draw already existed, OFFICIAL arrived later and created a conflict.
        // If user edits the manual numbers, keep the conflict row and update the manual snapshot.
        if (savedDraw.getOrigin() == DrawOrigin.MANUAL && existingConflictOpt.isPresent()) {
            DrawConflict c = existingConflictOpt.get();
            c.setManualWhiteJson(writeJson(manualW));
            c.setManualRedJson(writeJson(manualR));
            drawConflictRepository.save(c);
        }
    }

    private void createOrUpdateConflict(GameMode mode, DrawResult draw, List<Integer> manualW, List<Integer> manualR, List<Integer> officialW, List<Integer> officialR) {
        DrawConflict conflict = drawConflictRepository.findByDrawResultId(draw.getId())
                .orElseGet(() -> DrawConflict.builder()
                        .drawResult(draw)
                        .gameModeId(mode.getId())
                        .drawDate(draw.getDrawDate())
                        .build());

        conflict.setManualWhiteJson(writeJson(manualW));
        conflict.setManualRedJson(writeJson(manualR));
        conflict.setOfficialWhiteJson(writeJson(officialW));
        conflict.setOfficialRedJson(writeJson(officialR));

        // If new official arrives later, re-surface conflict (optional); for now keep ack state.
        drawConflictRepository.save(conflict);
    }

    private void addPicks(DrawResult draw, List<Integer> white, List<Integer> red) {
        int pos = 1;
        if (white != null) {
            for (Integer n : white) {
                if (n == null) continue;
                draw.getPicks().add(DrawPick.builder()
                        .drawResult(draw)
                        .poolType(com.lotteryapp.lottery.domain.numbers.PoolType.WHITE)
                        .position(pos++)
                        .numberValue(n)
                        .build());
            }
        }

        pos = 1;
        if (red != null) {
            for (Integer n : red) {
                if (n == null) continue;
                draw.getPicks().add(DrawPick.builder()
                        .drawResult(draw)
                        .poolType(com.lotteryapp.lottery.domain.numbers.PoolType.RED)
                        .position(pos++)
                        .numberValue(n)
                        .build());
            }
        }
    }

    private DrawResponse toDrawResponse(DrawResult draw) {
        DrawConflict conflict = (draw.getId() == null) ? null : drawConflictRepository.findByDrawResultId(draw.getId()).orElse(null);

        List<Integer> white = draw.getPicks().stream()
                .filter(p -> p.getPoolType() == com.lotteryapp.lottery.domain.numbers.PoolType.WHITE)
                .sorted(Comparator.comparingInt(DrawPick::getPosition))
                .map(DrawPick::getNumberValue)
                .toList();

        List<Integer> red = draw.getPicks().stream()
                .filter(p -> p.getPoolType() == com.lotteryapp.lottery.domain.numbers.PoolType.RED)
                .sorted(Comparator.comparingInt(DrawPick::getPosition))
                .map(DrawPick::getNumberValue)
                .toList();

        return DrawResponse.builder()
                .id(draw.getId())
                .drawDate(draw.getDrawDate())
                .origin(draw.getOrigin())
                .whiteNumbers(white)
                .redNumbers(red)
                .hasConflict(conflict != null)
                .conflictId(conflict == null ? null : conflict.getId())
                .conflictAcknowledged(conflict != null && conflict.isAcknowledged())
                .build();
    }

    private DrawConflictResponse toConflictResponse(DrawConflict c) {
        return DrawConflictResponse.builder()
                .id(c.getId())
                .drawResultId(c.getDrawResult().getId())
                .gameModeId(c.getGameModeId())
                .drawDate(c.getDrawDate())
                .manualWhite(readIntList(c.getManualWhiteJson()))
                .manualRed(readIntList(c.getManualRedJson()))
                .officialWhite(readIntList(c.getOfficialWhiteJson()))
                .officialRed(readIntList(c.getOfficialRedJson()))
                .acknowledged(c.isAcknowledged())
                .build();
    }

    // -----------------------------
    // schedule helpers
    // -----------------------------

    private static BigDecimal toBigDecimal(Long value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }

    private void onDrawActiveForNumberBalls(GameMode mode, List<DrawResult> draws) {
        if (draws == null || draws.isEmpty()) return;

        List<NumberBall> balls = numberBallService.getBallsByGameModeId(mode.getId());
        numberBallLifecycleService.applydraw(mode, draws, balls);
        numberBallService.saveAll(balls);
    }

    private LocalDate computeLatestExpectedDrawDate(GameMode mode) {
        LocalDate today = LocalDate.now();

        Set<DrawDay> drawDays = mode.getDrawDays();
        if (drawDays == null || drawDays.isEmpty()) return today;

        ZoneId zone = safeZone(mode.getDrawTimeZoneId());
        LocalTime time = mode.getDrawTimeLocal();

        // If time+zone are available, only count today's draw if draw time has passed in that zone.
        if (time != null && zone != null) {
            ZonedDateTime nowZ = ZonedDateTime.now(zone);

            LocalDate candidate = today;
            for (int i = 0; i < 14; i++) { // look back up to 2 weeks
                if (drawDays.contains(mapDay(candidate.getDayOfWeek()))) {
                    ZonedDateTime drawMoment = candidate.atTime(time).atZone(zone);
                    if (!drawMoment.isAfter(nowZ)) return candidate;
                }
                candidate = candidate.minusDays(1);
            }
        }

        // fallback: most recent scheduled day on/before today
        LocalDate d = today;
        for (int i = 0; i < 14; i++) {
            if (drawDays.contains(mapDay(d.getDayOfWeek()))) return d;
            d = d.minusDays(1);
        }
        return today;
    }

    private LocalDate computeNextDrawDate(GameMode mode) {
        Set<DrawDay> drawDays = mode.getDrawDays();
        if (drawDays == null || drawDays.isEmpty()) return null;

        LocalDate start = LocalDate.now();
        for (int i = 0; i < 14; i++) {
            LocalDate d = start.plusDays(i);
            if (drawDays.contains(mapDay(d.getDayOfWeek()))) return d;
        }
        return null;
    }

    private int estimateMissingDrawCount(GameMode mode, LocalDate latestStored, LocalDate latestExpected) {
        if (latestExpected == null) return 0;
        if (latestStored == null) {
            // missing from formatStartDate? keep it simple here
            return 1;
        }
        if (!latestStored.isBefore(latestExpected)) return 0;

        return (int) computeMissingDrawDates(mode, latestStored, latestExpected).size();
    }

    private List<LocalDate> computeMissingDrawDates(GameMode mode, LocalDate latestStored, LocalDate latestExpected) {
        if (latestExpected == null) return List.of();
        if (latestStored == null) return List.of(latestExpected);

        if (!latestStored.isBefore(latestExpected)) return List.of();

        Set<DrawDay> drawDays = mode.getDrawDays();
        LocalDate d = latestStored.plusDays(1);

        List<LocalDate> out = new ArrayList<>();
        while (!d.isAfter(latestExpected)) {
            if (drawDays.contains(mapDay(d.getDayOfWeek()))) out.add(d);
            d = d.plusDays(1);
        }
        return out;
    }

    private static DrawDay mapDay(DayOfWeek dow) {
        return switch (dow) {
            case MONDAY -> DrawDay.MONDAY;
            case TUESDAY -> DrawDay.TUESDAY;
            case WEDNESDAY -> DrawDay.WEDNESDAY;
            case THURSDAY -> DrawDay.THURSDAY;
            case FRIDAY -> DrawDay.FRIDAY;
            case SATURDAY -> DrawDay.SATURDAY;
            case SUNDAY -> DrawDay.SUNDAY;
        };
    }

    private static ZoneId safeZone(String zoneId) {
        if (zoneId == null || zoneId.isBlank()) return null;
        try {
            return ZoneId.of(zoneId);
        } catch (Exception e) {
            return null;
        }
    }

    // -----------------------------
    // validation + utils
    // -----------------------------

    private boolean isManualRequest(UpsertDrawRequest r) {
        return (r.getWhiteNumbers() != null && !r.getWhiteNumbers().isEmpty())
                || (r.getRedNumbers() != null && !r.getRedNumbers().isEmpty())
                || r.getJackpotAmount() != null
                || r.getCashValue() != null
                || r.getDrawTimeLocal() != null
                || (r.getDrawTimeZoneId() != null && !r.getDrawTimeZoneId().isBlank());
    }

    private void validateNumbers(GameMode mode, List<Integer> white, List<Integer> red) {
        if (mode.getRules() == null) throw new BadRequestException("Rules are required to validate manual draws");

        var rules = mode.getRules();

        if (white != null && !white.isEmpty()) {
            if (rules.getWhitePickCount() != null && white.size() != rules.getWhitePickCount()) {
                throw new BadRequestException("whiteNumbers must have exactly " + rules.getWhitePickCount() + " values");
            }
            for (Integer n : white) {
                if (n == null) continue;
                if (n < rules.getWhiteMin() || n > rules.getWhiteMax()) {
                    throw new BadRequestException("whiteNumbers contains out-of-range value: " + n);
                }
            }
        }

        if (red != null && !red.isEmpty()) {
            if (rules.getRedPickCount() != null && red.size() != rules.getRedPickCount()) {
                throw new BadRequestException("redNumbers must have exactly " + rules.getRedPickCount() + " values");
            }
            if (rules.getRedMin() != null && rules.getRedMax() != null) {
                for (Integer n : red) {
                    if (n == null) continue;
                    if (n < rules.getRedMin() || n > rules.getRedMax()) {
                        throw new BadRequestException("redNumbers contains out-of-range value: " + n);
                    }
                }
            }
        }
    }

    private GameMode requireMode(Long id) {
        if (id == null) throw new BadRequestException("gameModeId is required");
        return gameModeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("GameMode not found"));
    }

    private boolean isLatest(GameMode mode, LocalDate drawDate) {
        return mode.getLatestDrawDate() == null || !drawDate.isBefore(mode.getLatestDrawDate());
    }

    private static String csv(List<Integer> nums) {
        if (nums == null || nums.isEmpty()) return null;
        return nums.stream().filter(Objects::nonNull).map(String::valueOf).collect(Collectors.joining(","));
    }

    private String writeJson(List<Integer> nums) {
        try {
            if (nums == null) return null;
            return objectMapper.writeValueAsString(nums);
        } catch (Exception e) {
            throw new BadRequestException("Failed to write JSON");
        }
    }

    private List<Integer> readIntList(String json) {
        try {
            if (json == null || json.isBlank()) return List.of();
            return objectMapper.readValue(json, new TypeReference<List<Integer>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private static boolean sameNumbers(List<Integer> mw, List<Integer> mr, List<Integer> ow, List<Integer> orr) {
        return normalize(mw).equals(normalize(ow)) && normalize(mr).equals(normalize(orr));
    }

    private static List<Integer> normalize(List<Integer> in) {
        if (in == null) return List.of();
        return in.stream().filter(Objects::nonNull).toList();
    }

    private static List<Integer> picksToList(DrawResult draw, String pool) {
        var pt = "WHITE".equals(pool)
                ? com.lotteryapp.lottery.domain.numbers.PoolType.WHITE
                : com.lotteryapp.lottery.domain.numbers.PoolType.RED;

        return draw.getPicks().stream()
                .filter(p -> p.getPoolType() == pt)
                .sorted(Comparator.comparingInt(DrawPick::getPosition))
                .map(DrawPick::getNumberValue)
                .toList();
    }

    private GameModeResponse toGameModeResponse(GameMode m) {
        return GameModeResponse.builder()
                .id(m.getId())
                .modeKey(m.getModeKey())
                .displayName(m.getDisplayName())
                .scope(m.getScope())
                .jurisdictionCode(m.getJurisdiction() == null ? null : m.getJurisdiction().getCode())
                .rulesId(m.getRules() == null ? null : m.getRules().getId())
                .tierRangeStartDate(m.getTierRangeStartDate())
                .tierRangeEndDate(m.getTierRangeEndDate())
                .drawDays(m.getDrawDays())
                .nextDrawDate(m.getNextDrawDate())
                .drawTimeLocal(m.getDrawTimeLocal())
                .drawTimeZoneId(m.getDrawTimeZoneId())
                .latestDrawDate(m.getLatestDrawDate())
                .latestWhiteWinningCsv(m.getLatestWhiteWinningCsv())
                .latestRedWinningCsv(m.getLatestRedWinningCsv())
                .latestJackpotAmount(m.getLatestJackpotAmount())
                .latestCashValue(m.getLatestCashValue())
                .status(m.getStatus())
                .build();
    }


}
