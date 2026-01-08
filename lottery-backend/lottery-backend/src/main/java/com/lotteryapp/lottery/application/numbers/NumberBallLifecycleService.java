package com.lotteryapp.lottery.application.numbers;

import com.lotteryapp.lottery.domain.draw.DrawPick;
import com.lotteryapp.lottery.domain.draw.DrawResult;
import com.lotteryapp.lottery.domain.gamemode.GameMode;
import com.lotteryapp.lottery.domain.gamemode.Rules;
import com.lotteryapp.lottery.domain.numbers.NumberBall;
import com.lotteryapp.lottery.domain.numbers.PoolType;
import com.lotteryapp.lottery.domain.numbers.Tier;
import com.lotteryapp.lottery.domain.numbers.tier.NumberBallTierEngine;
import com.lotteryapp.lottery.domain.numbers.tier.TierCutoffs;
import com.lotteryapp.lottery.domain.numbers.tier.TierWindow;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Orchestrates NumberBall cache:
 * - initialize/rebuild from draw history
 * - incrementally update on new draw results
 * - recalculate tiers based on GameMode tier window
 *
 * NOTE: This class references repositories you will create next.
 * It’s the correct shape for the lifecycle; we’ll wire repos when you’re ready.
 */
public class NumberBallLifecycleService {

    // TODO: inject repositories later
    // private final GameModeRepository gameModeRepo;
    // private final DrawResultRepository drawResultRepo;
    // private final NumberBallRepository numberBallRepo;

    private final TierCutoffs cutoffs = TierCutoffs.defaultCutoffs();

    /**
     * Rebuilds NumberBall rows and recomputes totals + tierCounts + tiers for a single game mode.
     * Used for first-time setup and after Rules/format changes.
     */
    public void rebuildForGameMode(GameMode mode, List<DrawResult> historyDraws) {
        Rules rules = requireRules(mode);

        // 1) Ensure all NumberBall rows exist (white pool + optional red pool)
        //    (repo layer will persist these)
        List<NumberBall> balls = initializeBalls(mode, rules);

        // 2) Compute totalCount + lastDrawnDate from history draws (format history)
        applyCountsFromDraws(balls, historyDraws, /*total=*/true);

        // 3) Compute tier window and tierCount from draws inside that window
        TierWindow window = GameModeTierWindowResolver.resolve(mode, LocalDate.now());
        List<DrawResult> windowDraws = filterDrawsInWindow(historyDraws, window);
        applyCountsFromDraws(balls, windowDraws, /*total=*/false);

        // 4) Assign tiers + statusChange
        NumberBallTierEngine.assignTiers(balls, cutoffs);

        // repo.saveAll(balls)
    }

    /**
     * Incremental update when a new DrawResult is saved.
     * - bump totalCount
     * - bump tierCount only if drawDate within current tier window
     * - update lastDrawnDate
     * - then recalc tiers (or schedule it) for that game/pool
     */
    public void applyNewDraw(GameMode mode, DrawResult newDraw, List<NumberBall> currentBalls) {
        requireRules(mode);

        // bump totals for picks
        bumpCounts(currentBalls, newDraw.getPicks(), true);

        // bump tier counts if within window
        TierWindow window = GameModeTierWindowResolver.resolve(mode, LocalDate.now());
        if (!newDraw.getDrawDate().isBefore(window.start()) && !newDraw.getDrawDate().isAfter(window.end())) {
            bumpCounts(currentBalls, newDraw.getPicks(), false);
        }

        // recompute tiers after update (could be async later; for now do it inline)
        NumberBallTierEngine.assignTiers(currentBalls, cutoffs);

        // repo.saveAll(currentBalls)
    }

    /**
     * Recalculate tierCount + tiers for the current tier window without changing totalCount.
     * Useful after user changes GameMode tierRangeStartDate/tierRangeEndDate.
     */
    public void recalculateTiers(GameMode mode, List<DrawResult> drawsInFormatHistory, List<NumberBall> balls) {
        requireRules(mode);

        // reset tierCount to 0 before recomputing window counts
        for (NumberBall b : balls) b.setTierCount(0);

        TierWindow window = GameModeTierWindowResolver.resolve(mode, LocalDate.now());
        List<DrawResult> windowDraws = filterDrawsInWindow(drawsInFormatHistory, window);
        applyCountsFromDraws(balls, windowDraws, /*total=*/false);

        NumberBallTierEngine.assignTiers(balls, cutoffs);

        // repo.saveAll(balls)
    }

    // -------------------
    // Helpers
    // -------------------

    private Rules requireRules(GameMode mode) {
        Rules rules = mode.getRules();
        if (rules == null) throw new IllegalStateException("GameMode.rules required.");
        return rules;
    }

    private List<NumberBall> initializeBalls(GameMode mode, Rules rules) {
        List<NumberBall> balls = new ArrayList<>();

        // WHITE
        for (int v = rules.getWhiteMin(); v <= rules.getWhiteMax(); v++) {
            balls.add(newBall(mode, PoolType.WHITE, v));
        }

        // RED (optional)
        if (rules.getRedPickCount() != null && rules.getRedPickCount() > 0
                && rules.getRedMin() != null && rules.getRedMax() != null) {
            for (int v = rules.getRedMin(); v <= rules.getRedMax(); v++) {
                balls.add(newBall(mode, PoolType.RED, v));
            }
        }

        return balls;
    }

    private NumberBall newBall(GameMode mode, PoolType poolType, int value) {
        NumberBall b = new NumberBall();
        b.setGameMode(mode);
        b.setPoolType(poolType);
        b.setNumberValue(value);

        b.setTier(Tier.COLD);
        b.setTotalCount(0);
        b.setTierCount(0);
        b.setLastDrawnDate(null);
        // statusChange will be set during tier assignment
        return b;
    }

    private List<DrawResult> filterDrawsInWindow(List<DrawResult> draws, TierWindow window) {
        return draws.stream()
                .filter(d -> !d.getDrawDate().isBefore(window.start()) && !d.getDrawDate().isAfter(window.end()))
                .toList();
    }

    private void applyCountsFromDraws(List<NumberBall> balls, List<DrawResult> draws, boolean total) {
        for (DrawResult d : draws) {
            bumpCounts(balls, d.getPicks(), total);
        }
    }

    private void bumpCounts(List<NumberBall> balls, List<DrawPick> picks, boolean total) {
        // Build lookup map by (poolType, numberValue)
        Map<String, NumberBall> map = balls.stream()
                .collect(Collectors.toMap(
                        b -> b.getPoolType() + ":" + b.getNumberValue(),
                        b -> b
                ));

        for (DrawPick p : picks) {
            String key = p.getPoolType() + ":" + p.getNumberValue();
            NumberBall b = map.get(key);
            if (b == null) continue;

            if (total) b.setTotalCount(safeInt(b.getTotalCount()) + 1);
            else b.setTierCount(safeInt(b.getTierCount()) + 1);

            // Update lastDrawnDate (recency)
            if (b.getLastDrawnDate() == null || p.getDrawResult().getDrawDate().isAfter(b.getLastDrawnDate())) {
                b.setLastDrawnDate(p.getDrawResult().getDrawDate());
            }
        }
    }

    private int safeInt(Integer v) {
        return v == null ? 0 : v;
    }
}
