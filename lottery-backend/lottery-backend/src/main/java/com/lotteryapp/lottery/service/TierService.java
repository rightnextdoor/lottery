package com.lotteryapp.lottery.service;

import com.lotteryapp.common.exception.BadRequestException;
import com.lotteryapp.common.exception.NotFoundException;
import com.lotteryapp.lottery.application.numbers.GameModeTierWindowResolver;
import com.lotteryapp.lottery.application.numbers.NumberBallLifecycleService;
import com.lotteryapp.lottery.domain.draw.DrawResult;
import com.lotteryapp.lottery.domain.gamemode.GameMode;
import com.lotteryapp.lottery.domain.numbers.NumberBall;
import com.lotteryapp.lottery.domain.numbers.PoolType;
import com.lotteryapp.lottery.domain.numbers.Tier;
import com.lotteryapp.lottery.domain.numbers.tier.TierWindow;
import com.lotteryapp.lottery.dto.common.ApiResponse;
import com.lotteryapp.lottery.dto.numberball.response.NumberBallResponse;
import com.lotteryapp.lottery.dto.tier.request.GetTierListRequest;
import com.lotteryapp.lottery.dto.tier.request.GetTierMatrixRequest;
import com.lotteryapp.lottery.dto.tier.request.UpdateTierRangeRequest;
import com.lotteryapp.lottery.dto.tier.response.TierMatrixResponse;
import com.lotteryapp.lottery.dto.tier.response.TierPoolResponse;
import com.lotteryapp.lottery.dto.tier.response.UpdateTierRangeResponse;
import com.lotteryapp.lottery.repository.DrawResultRepository;
import com.lotteryapp.lottery.repository.GameModeRepository;
import com.lotteryapp.lottery.repository.NumberBallRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TierService {

    private final GameModeRepository gameModeRepository;
    private final DrawResultRepository drawResultRepository;
    private final NumberBallRepository numberBallRepository;

    private final NumberBallService numberBallService;
    private final NumberBallLifecycleService numberBallLifecycleService;

    private static final Sort TIER_SORT = Sort.by(
            Sort.Order.desc("tierCount"),
            Sort.Order.desc("lastDrawnDate"),
            Sort.Order.asc("numberValue")
    );

    public ApiResponse<TierMatrixResponse> getTierMatrix(GetTierMatrixRequest request) {
        Long gameModeId = request.getGameModeId();

        gameModeRepository.findById(gameModeId).orElseThrow(() ->
                new NotFoundException(
                        "GameMode not found",
                        "GAME_MODE_NOT_FOUND",
                        Map.of("gameModeId", gameModeId)
                )
        );

        TierPoolResponse white = buildPool(gameModeId, PoolType.WHITE);
        TierPoolResponse red = buildPool(gameModeId, PoolType.RED);

        return ApiResponse.ok("Tiers loaded", new TierMatrixResponse(white, red));
    }

    public ApiResponse<List<NumberBallResponse>> getTierList(GetTierListRequest request) {
        Long gameModeId = request.getGameModeId();

        gameModeRepository.findById(gameModeId).orElseThrow(() ->
                new NotFoundException(
                        "GameMode not found",
                        "GAME_MODE_NOT_FOUND",
                        Map.of("gameModeId", gameModeId)
                )
        );

        List<NumberBallResponse> list = numberBallRepository
                .findByGameModeIdAndPoolTypeAndTier(gameModeId, request.getPoolType(), request.getTier(), TIER_SORT)
                .stream()
                .map(this::toResponse)
                .toList();

        return ApiResponse.ok("Tier list loaded", list);
    }

    /**
     * Category 5 hook:
     * TierController updates GameMode tier range dates through this service,
     * then we trigger lifecycle.recalculateTiers(...) with:
     * - GameMode (must have Rules)
     * - draw history (service loads)
     * - whiteBalls/redBalls (service loads)
     * Then we save mutated balls (service persists).
     */
    public ApiResponse<UpdateTierRangeResponse> updateTierRange(UpdateTierRangeRequest request) {
        Long gameModeId = request.getGameModeId();

        GameMode gm = gameModeRepository.findById(gameModeId).orElseThrow(() ->
                new NotFoundException(
                        "GameMode not found",
                        "GAME_MODE_NOT_FOUND",
                        Map.of("gameModeId", gameModeId)
                )
        );

        validateTierRangeDates(request.getTierRangeStartDate(), request.getTierRangeEndDate());

        gm.setTierRangeStartDate(request.getTierRangeStartDate());
        gm.setTierRangeEndDate(request.getTierRangeEndDate());

        GameMode saved = gameModeRepository.save(gm);

        TierWindow window = GameModeTierWindowResolver.resolve(saved, LocalDate.now());

        List<DrawResult> history = drawResultRepository
                .findByGameModeIdAndDrawDateBetweenOrderByDrawDateAsc(saved.getId(), window.start(), window.end());

        List<NumberBall> balls = numberBallService.getBallsByGameModeId(saved.getId());

        numberBallLifecycleService.recalculateTiers(saved, history, balls);

        numberBallService.saveAll(balls);

        return ApiResponse.ok(
                "Tier range updated",
                new UpdateTierRangeResponse(saved.getId(), saved.getTierRangeStartDate(), saved.getTierRangeEndDate())
        );
    }

    private void validateTierRangeDates(LocalDate start, LocalDate end) {
        LocalDate today = LocalDate.now();

        if (start != null && start.isAfter(today)) {
            throw new BadRequestException(
                    "tierRangeStartDate cannot be in the future",
                    "INVALID_TIER_RANGE",
                    Map.of("tierRangeStartDate", start, "today", today)
            );
        }

        if (end != null && end.isAfter(today)) {
            throw new BadRequestException(
                    "tierRangeEndDate cannot be in the future",
                    "INVALID_TIER_RANGE",
                    Map.of("tierRangeEndDate", end, "today", today)
            );
        }

        if (start != null && end != null && start.isAfter(end)) {
            throw new BadRequestException(
                    "tierRangeStartDate must be <= tierRangeEndDate",
                    "INVALID_TIER_RANGE",
                    Map.of("tierRangeStartDate", start, "tierRangeEndDate", end)
            );
        }
    }

    private TierPoolResponse buildPool(Long gameModeId, PoolType poolType) {
        List<NumberBallResponse> hot = numberBallRepository
                .findByGameModeIdAndPoolTypeAndTier(gameModeId, poolType, Tier.HOT, TIER_SORT)
                .stream().map(this::toResponse).toList();

        List<NumberBallResponse> mid = numberBallRepository
                .findByGameModeIdAndPoolTypeAndTier(gameModeId, poolType, Tier.MID, TIER_SORT)
                .stream().map(this::toResponse).toList();

        List<NumberBallResponse> cold = numberBallRepository
                .findByGameModeIdAndPoolTypeAndTier(gameModeId, poolType, Tier.COLD, TIER_SORT)
                .stream().map(this::toResponse).toList();

        return new TierPoolResponse(hot, mid, cold);
    }

    private NumberBallResponse toResponse(NumberBall b) {
        Long gmId = (b.getGameMode() == null) ? null : b.getGameMode().getId();

        return new NumberBallResponse(
                b.getId(),
                gmId,
                b.getPoolType(),
                b.getNumberValue(),
                b.getLastDrawnDate(),
                b.getTotalCount(),
                b.getTierCount(),
                b.getTier(),
                b.getStatusChange()
        );
    }
}
