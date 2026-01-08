package com.lotteryapp.lottery.domain.numbers.tier;

import com.lotteryapp.lottery.domain.numbers.NumberBall;
import com.lotteryapp.lottery.domain.numbers.StatusChange;
import com.lotteryapp.lottery.domain.numbers.Tier;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class NumberBallTierEngine {

    private NumberBallTierEngine() {}

    /**
     * Assigns tiers based on tierCount (descending) with a recency tie-break using lastDrawnDate (descending).
     *
     * Rules:
     * - Every ball gets a tier.
     * - Bucket sizes derived from cutoffs (hotPct, midPct). Remainder is cold.
     * - Tie handling at bucket boundaries:
     *    If cutoff splits a tied tierCount group, keep the most recently drawn balls in the higher tier.
     * - Edge case: if Hot+Mid end up empty (or all tierCount==0), everything becomes COLD (acts like quick-pick).
     *
     * Updates:
     * - ball.tier
     * - ball.statusChange (PROMOTED/DEMOTED/NONE) based on previous tier.
     */
    public static void assignTiers(List<NumberBall> balls, TierCutoffs cutoffs) {
        if (balls == null || balls.isEmpty()) return;

        // Sort: tierCount desc, lastDrawnDate desc (nulls last), numberValue asc (stable)
        List<NumberBall> sorted = new ArrayList<>(balls);
        sorted.sort(Comparator
                .comparingInt(NumberBallTierEngine::safeTierCount).reversed()
                .thenComparing(NumberBallTierEngine::safeLastDrawnDate, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparingInt(NumberBallTierEngine::safeNumberValue)
        );

        // If all tierCount are 0 -> everyone cold (your edge case)
        boolean allZero = sorted.stream().allMatch(b -> safeTierCount(b) == 0);
        if (allZero) {
            setAll(sorted, Tier.COLD);
            return;
        }

        int n = sorted.size();
        int hotTarget = (int) Math.round(n * (cutoffs.hotPct() / 100.0));
        int midTarget = (int) Math.round(n * (cutoffs.midPct() / 100.0));

        // Clamp to valid ranges
        hotTarget = Math.max(0, Math.min(hotTarget, n));
        midTarget = Math.max(0, Math.min(midTarget, n - hotTarget));

        // Determine actual HOT set with tie-safe boundary
        Set<NumberBall> hotSet = pickTopWithTieSafety(sorted, hotTarget, NumberBallTierEngine::safeTierCount);

        // Determine MID candidates from the remaining
        List<NumberBall> remainingAfterHot = sorted.stream()
                .filter(b -> !hotSet.contains(b))
                .toList();

        Set<NumberBall> midSet = pickTopWithTieSafety(remainingAfterHot, midTarget, NumberBallTierEngine::safeTierCount);

        // Everything else is COLD
        for (NumberBall b : sorted) {
            Tier previous = b.getTier();
            Tier next = hotSet.contains(b) ? Tier.HOT : (midSet.contains(b) ? Tier.MID : Tier.COLD);
            applyTierAndStatusChange(b, previous, next);
        }

        // If somehow hot+mid empty, force cold (defensive)
        if (hotSet.isEmpty() && midSet.isEmpty()) {
            setAll(sorted, Tier.COLD);
        }
    }

    /**
     * Picks the first targetCount, but if the boundary splits a tied group (same score),
     * keep the most recent in the higher tier.
     */
    private static Set<NumberBall> pickTopWithTieSafety(List<NumberBall> sorted, int targetCount,
                                                        Function<NumberBall, Integer> scoreFn) {
        if (targetCount <= 0 || sorted.isEmpty()) return Collections.emptySet();
        if (targetCount >= sorted.size()) return new LinkedHashSet<>(sorted);

        // First pass: take first targetCount
        List<NumberBall> top = new ArrayList<>(sorted.subList(0, targetCount));

        // Boundary score = score at last included
        int boundaryScore = scoreFn.apply(top.get(top.size() - 1));

        // Find all balls tied at boundaryScore across whole list
        List<NumberBall> tied = sorted.stream()
                .filter(b -> scoreFn.apply(b) == boundaryScore)
                .collect(Collectors.toList());

        // If boundary score is unique (only appears inside top, no split) -> ok
        boolean split = tied.stream().anyMatch(b -> !top.contains(b)) && top.stream().anyMatch(b -> tied.contains(b));
        if (!split) {
            return new LinkedHashSet<>(top);
        }

        // Rebuild by:
        //  - include everything strictly above boundaryScore
        //  - then fill remaining slots from tied group by recency (lastDrawnDate desc), then numberValue asc
        List<NumberBall> above = sorted.stream()
                .filter(b -> scoreFn.apply(b) > boundaryScore)
                .toList();

        int remainingSlots = targetCount - above.size();
        if (remainingSlots <= 0) {
            return new LinkedHashSet<>(above.subList(0, targetCount));
        }

        tied.sort(Comparator
                .comparing(NumberBallTierEngine::safeLastDrawnDate, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparingInt(NumberBallTierEngine::safeNumberValue)
        );

        List<NumberBall> picked = new ArrayList<>(above);
        for (NumberBall b : tied) {
            if (picked.size() >= targetCount) break;
            if (!picked.contains(b)) picked.add(b);
        }

        // Safety: if still short, fill from remainder by original order
        if (picked.size() < targetCount) {
            for (NumberBall b : sorted) {
                if (picked.size() >= targetCount) break;
                if (!picked.contains(b)) picked.add(b);
            }
        }

        return new LinkedHashSet<>(picked);
    }

    private static void setAll(List<NumberBall> balls, Tier tier) {
        for (NumberBall b : balls) {
            Tier previous = b.getTier();
            applyTierAndStatusChange(b, previous, tier);
            b.setTierCount(safeTierCount(b)); // no change; keep tierCount computed elsewhere
        }
    }

    private static void applyTierAndStatusChange(NumberBall b, Tier previous, Tier next) {
        b.setTier(next);

        // StatusChange based on movement (service will have computed previous tier already stored on entity)
        if (previous == null || previous == next) {
            b.setStatusChange(StatusChange.NONE);
            return;
        }

        // Define ordering HOT > MID > COLD
        int prevRank = tierRank(previous);
        int nextRank = tierRank(next);

        if (nextRank > prevRank) b.setStatusChange(StatusChange.PROMOTED);
        else if (nextRank < prevRank) b.setStatusChange(StatusChange.DEMOTED);
        else b.setStatusChange(StatusChange.NONE);
    }

    private static int tierRank(Tier tier) {
        return switch (tier) {
            case COLD -> 0;
            case MID -> 1;
            case HOT -> 2;
        };
    }

    private static int safeTierCount(NumberBall b) {
        return b.getTierCount() == null ? 0 : b.getTierCount();
    }

    private static int safeNumberValue(NumberBall b) {
        return b.getNumberValue() == null ? -1 : b.getNumberValue();
    }

    private static LocalDate safeLastDrawnDate(NumberBall b) {
        return b.getLastDrawnDate();
    }
}
