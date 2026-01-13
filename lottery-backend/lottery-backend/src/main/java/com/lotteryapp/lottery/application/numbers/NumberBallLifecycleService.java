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
import com.lotteryapp.lottery.service.DrawService;
import com.lotteryapp.lottery.service.NumberBallService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class NumberBallLifecycleService {

    private final NumberBallService numberBallService;

    private final TierCutoffs cutoffs = TierCutoffs.defaultCutoffs();

    /**
     * Rebuilds NumberBall rows and recomputes totals + tierCounts + tiers for a single game mode.
     * Used for first-time setup and after Rules/format changes.
     */
    public void rebuildForGameMode(GameMode mode) {
        requireRules(mode);

        numberBallService.initializeForGameMode(mode);
    }

    /**
     * Incremental update when a new DrawResult is saved.
     * - bump totalCount
     * - bump tierCount only if drawDate within current tier window
     * - update lastDrawnDate
     * - then recalc tiers (or schedule it) for that game/pool
     */
    public void applydraw(GameMode mode, List<DrawResult> draws, List<NumberBall> balls) {
        requireRules(mode);

        applyCountsFromDraws(balls, draws, true);

        TierWindow window = GameModeTierWindowResolver.resolve(mode, LocalDate.now());
        for(DrawResult draw: draws){
            if (draw == null || draw.getDrawDate() == null) continue;

            if (!draw.getDrawDate().isBefore(window.start()) && !draw.getDrawDate().isAfter(window.end())) {
                bumpCounts(balls, draw.getPicks(), false);
            }
        }

        NumberBallTierEngine.assignTiers(balls, cutoffs);

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
        applyCountsFromDraws(balls, windowDraws, false);

        NumberBallTierEngine.assignTiers(balls, cutoffs);

    }

    // -------------------
    // Helpers
    // -------------------

    private Rules requireRules(GameMode mode) {
        Rules rules = mode.getRules();
        if (rules == null) throw new IllegalStateException("GameMode.rules required.");
        return rules;
    }

    private List<DrawResult> filterDrawsInWindow(List<DrawResult> draws, TierWindow window) {
        return draws.stream()
                .filter(d -> d != null && d.getDrawDate() != null)
                .filter(d -> !d.getDrawDate().isBefore(window.start()) && !d.getDrawDate().isAfter(window.end()))
                .toList();
    }

    private void applyCountsFromDraws(List<NumberBall> balls, List<DrawResult> draws, boolean total) {
        for (DrawResult d : draws) {
            if (d == null || d.getPicks() == null) continue;
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
