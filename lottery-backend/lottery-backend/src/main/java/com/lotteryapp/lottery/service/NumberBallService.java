package com.lotteryapp.lottery.service;

import com.lotteryapp.common.exception.BadRequestException;
import com.lotteryapp.common.exception.NotFoundException;
import com.lotteryapp.lottery.domain.gamemode.GameMode;
import com.lotteryapp.lottery.domain.gamemode.Rules;
import com.lotteryapp.lottery.domain.numbers.NumberBall;
import com.lotteryapp.lottery.domain.numbers.PoolType;
import com.lotteryapp.lottery.domain.numbers.StatusChange;
import com.lotteryapp.lottery.domain.numbers.Tier;
import com.lotteryapp.lottery.dto.common.ApiResponse;
import com.lotteryapp.lottery.dto.numberball.request.GetNumberBallDetailRequest;
import com.lotteryapp.lottery.dto.numberball.request.GetNumberBallsRequest;
import com.lotteryapp.lottery.dto.numberball.response.GetNumberBallsResponse;
import com.lotteryapp.lottery.dto.numberball.response.NumberBallResponse;
import com.lotteryapp.lottery.repository.GameModeRepository;
import com.lotteryapp.lottery.repository.NumberBallRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NumberBallService {

    private final GameModeRepository gameModeRepository;
    private final NumberBallRepository numberBallRepository;

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.asc("numberValue"));

    public ApiResponse<GetNumberBallsResponse> getNumberBalls(GetNumberBallsRequest request) {
        Long gameModeId = request.getGameModeId();

        gameModeRepository.findById(gameModeId).orElseThrow(() ->
                new NotFoundException(
                        "GameMode not found",
                        "GAME_MODE_NOT_FOUND",
                        Map.of("gameModeId", gameModeId)
                )
        );

        List<NumberBallResponse> white = numberBallRepository
                .findByGameModeIdAndPoolType(gameModeId, PoolType.WHITE, DEFAULT_SORT)
                .stream()
                .map(this::toResponse)
                .toList();

        List<NumberBallResponse> red = numberBallRepository
                .findByGameModeIdAndPoolType(gameModeId, PoolType.RED, DEFAULT_SORT)
                .stream()
                .map(this::toResponse)
                .toList();

        return ApiResponse.ok("NumberBalls loaded", new GetNumberBallsResponse(white, red));
    }

    public ApiResponse<NumberBallResponse> getNumberBallDetail(GetNumberBallDetailRequest request) {
        Long gameModeId = request.getGameModeId();

        gameModeRepository.findById(gameModeId).orElseThrow(() ->
                new NotFoundException(
                        "GameMode not found",
                        "GAME_MODE_NOT_FOUND",
                        Map.of("gameModeId", gameModeId)
                )
        );

        NumberBall ball = numberBallRepository
                .findByGameModeIdAndPoolTypeAndNumberValue(
                        gameModeId,
                        request.getPoolType(),
                        request.getNumberValue()
                )
                .orElseThrow(() -> new NotFoundException(
                        "NumberBall not found",
                        "NUMBER_BALL_NOT_FOUND",
                        Map.of(
                                "gameModeId", gameModeId,
                                "poolType", request.getPoolType(),
                                "numberValue", request.getNumberValue()
                        )
                ));

        return ApiResponse.ok("NumberBall loaded", toResponse(ball));
    }

    public List<NumberBall> initializeForGameMode(GameMode gameMode) {
        if (gameMode == null || gameMode.getId() == null) {
            throw new BadRequestException("GameMode is required", "GAME_MODE_REQUIRED", Map.of());
        }

        Rules rules = gameMode.getRules();
        if (rules == null) {
            throw new BadRequestException(
                    "Rules are required before initializing NumberBalls",
                    "RULES_REQUIRED",
                    Map.of("gameModeId", gameMode.getId())
            );
        }

        numberBallRepository.deleteByGameModeId(gameMode.getId());

        List<NumberBall> created = new ArrayList<>();

        if (rules.getWhiteMin() != null && rules.getWhiteMax() != null) {
            created.addAll(createPool(gameMode, PoolType.WHITE, rules.getWhiteMin(), rules.getWhiteMax()));
        }

        if (rules.getRedMin() != null && rules.getRedMax() != null) {
            created.addAll(createPool(gameMode, PoolType.RED, rules.getRedMin(), rules.getRedMax()));
        }

        return numberBallRepository.saveAll(created);
    }

    public List<NumberBall> getBallsByGameModeId(Long gameModeId) {
        gameModeRepository.findById(gameModeId).orElseThrow(() ->
                new NotFoundException(
                        "GameMode not found",
                        "GAME_MODE_NOT_FOUND",
                        Map.of("gameModeId", gameModeId)
                )
        );

        List<NumberBall> balls = numberBallRepository.findByGameModeId(gameModeId);

        // Stable ordering: poolType then numberValue
        balls.sort(Comparator
                .comparing(NumberBall::getPoolType, Comparator.nullsLast(Comparator.comparing(Enum::name)))
                .thenComparing(NumberBall::getNumberValue, Comparator.nullsLast(Integer::compareTo)));

        return balls;
    }
    public void saveAll(List<NumberBall> balls) {
        numberBallRepository.saveAll(balls);
    }

    private List<NumberBall> createPool(GameMode gameMode, PoolType poolType, Integer min, Integer max) {
        if (min == null || max == null || min > max) {
            throw new BadRequestException(
                    "Invalid pool range",
                    "INVALID_POOL_RANGE",
                    Map.of(
                            "gameModeId", gameMode.getId(),
                            "poolType", poolType,
                            "min", min,
                            "max", max
                    )
            );
        }

        List<NumberBall> list = new ArrayList<>(Math.max(0, max - min + 1));
        for (int n = min; n <= max; n++) {
            NumberBall b = new NumberBall();
            b.setGameMode(gameMode);
            b.setPoolType(poolType);
            b.setNumberValue(n);

            b.setLastDrawnDate(null);
            b.setTotalCount(0);
            b.setTierCount(0);

            // defaults; lifecycle tier engine will assign real tiers/status
            b.setTier(Tier.COLD);
            b.setStatusChange(StatusChange.NONE);

            list.add(b);
        }
        return list;
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
