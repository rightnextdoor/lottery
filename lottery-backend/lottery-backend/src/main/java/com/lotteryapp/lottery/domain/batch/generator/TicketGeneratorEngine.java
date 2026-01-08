package com.lotteryapp.lottery.domain.batch.generator;

import com.lotteryapp.lottery.domain.gamemode.Rules;
import com.lotteryapp.lottery.domain.group.GroupMode;
import com.lotteryapp.lottery.domain.group.TicketGroup;
import com.lotteryapp.lottery.domain.numbers.NumberBall;
import com.lotteryapp.lottery.domain.numbers.PoolType;
import com.lotteryapp.lottery.domain.numbers.Tier;

import java.util.*;
import java.util.stream.Collectors;

public class TicketGeneratorEngine {

    /**
     * Main entrypoint.
     */
    public GeneratedBatch generate(GeneratorRequest req) {
        Objects.requireNonNull(req, "req");

        Random rng = (req.options().randomSeed() == null)
                ? new Random()
                : new Random(req.options().randomSeed());

        GeneratedBatch out = new GeneratedBatch();

        // Used counts across the entire batch per pool (anti-dominance)
        Map<PoolType, Map<Integer, Integer>> usedCounts = new EnumMap<>(PoolType.class);
        usedCounts.put(PoolType.WHITE, new HashMap<>());
        usedCounts.put(PoolType.RED, new HashMap<>());

        for (int i = 0; i < req.ticketCount(); i++) {
            List<Integer> whites = generatePool(req, PoolType.WHITE, rng, usedCounts, out);
            List<Integer> reds = generatePool(req, PoolType.RED, rng, usedCounts, out);

            out.addTicket(new GeneratedTicket(
                    i,
                    whites,
                    reds,
                    req.whiteGroup() == null ? null : req.whiteGroup().getGroupKey(),
                    req.redGroup() == null ? null : req.redGroup().getGroupKey()
            ));
        }

        return out;
    }

    private List<Integer> generatePool(
            GeneratorRequest req,
            PoolType poolType,
            Random rng,
            Map<PoolType, Map<Integer, Integer>> usedCounts,
            GeneratedBatch out
    ) {
        Rules rules = req.rules();

        int min = (poolType == PoolType.WHITE) ? rules.getWhiteMin() : safeInt(rules.getRedMin());
        int max = (poolType == PoolType.WHITE) ? rules.getWhiteMax() : safeInt(rules.getRedMax());
        int pickCount = (poolType == PoolType.WHITE) ? rules.getWhitePickCount() : safeInt(rules.getRedPickCount());

        if (pickCount <= 0) return List.of(); // no RED pool, etc.

        boolean ordered = (poolType == PoolType.WHITE) ? bool(rules.getWhiteOrdered()) : bool(rules.getRedOrdered());
        boolean allowRepeats = (poolType == PoolType.WHITE) ? bool(rules.getWhiteAllowRepeats()) : bool(rules.getRedAllowRepeats());

        Set<Integer> excluded = req.excludedFor(poolType);

        if (req.mode() == GeneratorMode.QUICK_PICK) {
            return quickPick(min, max, pickCount, allowRepeats, ordered, excluded, rng, out, poolType);
        }

        // GROUP_WEIGHTED
        TicketGroup group = req.groupFor(poolType);
        List<NumberBall> balls = req.ballsFor(poolType);

        // If tier data isn't present or Hot+Mid empty, treat as quick pick for this pool
        TierBuckets buckets = TierBuckets.fromNumberBalls(balls, excluded);
        if (buckets.hot.isEmpty() && buckets.mid.isEmpty()) {
            out.warn(poolType + ": Hot+Mid empty; treating pool as quick pick (all cold).");
            return quickPick(min, max, pickCount, allowRepeats, ordered, excluded, rng, out, poolType);
        }

        // If no group specified for this pool, also fall back to quick pick
        if (group == null) {
            out.warn(poolType + ": No group provided; falling back to quick pick for this pool.");
            return quickPick(min, max, pickCount, allowRepeats, ordered, excluded, rng, out, poolType);
        }

        if (group.getPoolType() != poolType) {
            out.warn(poolType + ": Group poolType mismatch; falling back to quick pick for this pool.");
            return quickPick(min, max, pickCount, allowRepeats, ordered, excluded, rng, out, poolType);
        }

        if (group.getGroupMode() == GroupMode.COUNT) {
            return groupWeightedCount(
                    poolType, pickCount, allowRepeats, ordered,
                    group, buckets, rng, usedCounts.get(poolType), req.options(), out
            );
        } else {
            return groupWeightedPercent(
                    poolType, pickCount, allowRepeats, ordered,
                    group, buckets, rng, usedCounts.get(poolType), req.options(), out
            );
        }
    }

    // -------------------------
    // QUICK PICK
    // -------------------------

    private List<Integer> quickPick(
            int min,
            int max,
            int pickCount,
            boolean allowRepeats,
            boolean ordered,
            Set<Integer> excluded,
            Random rng,
            GeneratedBatch out,
            PoolType poolType
    ) {
        List<Integer> candidates = new ArrayList<>();
        for (int v = min; v <= max; v++) {
            if (!excluded.contains(v)) candidates.add(v);
        }

        if (!allowRepeats && candidates.size() < pickCount) {
            out.warn(poolType + ": exclusions made quick pick impossible; relaxing exclusions for this pool.");
            candidates.clear();
            for (int v = min; v <= max; v++) candidates.add(v);
        }

        if (candidates.isEmpty()) {
            // Repeats allowed but exclusions removed everything — relax
            out.warn(poolType + ": no candidates available; relaxing exclusions for this pool.");
            for (int v = min; v <= max; v++) candidates.add(v);
        }

        List<Integer> picks = new ArrayList<>(pickCount);

        if (allowRepeats) {
            for (int i = 0; i < pickCount; i++) {
                picks.add(candidates.get(rng.nextInt(candidates.size())));
            }
        } else {
            // sample without replacement uniformly
            Collections.shuffle(candidates, rng);
            picks.addAll(candidates.subList(0, pickCount));
        }

        if (!ordered) {
            Collections.sort(picks);
        }

        return picks;
    }

    // -------------------------
    // GROUP WEIGHTED: COUNT
    // -------------------------

    private List<Integer> groupWeightedCount(
            PoolType poolType,
            int pickCount,
            boolean allowRepeats,
            boolean ordered,
            TicketGroup group,
            TierBuckets buckets,
            Random rng,
            Map<Integer, Integer> usedCount,
            GeneratorOptions options,
            GeneratedBatch out
    ) {
        Integer hotN = safeNullable(group.getHotCount());
        Integer midN = safeNullable(group.getMidCount());
        Integer coldN = safeNullable(group.getColdCount());

        if (hotN + midN + coldN != pickCount) {
            out.warn(poolType + ": COUNT group does not match pickCount; attempting tier fall-down fill.");
        }

        List<Integer> picks = new ArrayList<>(pickCount);

        // pick from each tier with fall-down
        pickFromTierWithFallback(poolType, Tier.HOT, hotN, allowRepeats, picks, buckets, rng, usedCount, options, out);
        pickFromTierWithFallback(poolType, Tier.MID, midN, allowRepeats, picks, buckets, rng, usedCount, options, out);
        pickFromTierWithFallback(poolType, Tier.COLD, coldN, allowRepeats, picks, buckets, rng, usedCount, options, out);

        // If still short, fill remaining using tier fall-down from HOT->MID->COLD
        while (picks.size() < pickCount) {
            boolean ok = pickOneWeighted(poolType, allowRepeats, picks, buckets.hot, rng, usedCount, options)
                    || pickOneWeighted(poolType, allowRepeats, picks, buckets.mid, rng, usedCount, options)
                    || pickOneWeighted(poolType, allowRepeats, picks, buckets.cold, rng, usedCount, options);

            if (!ok) {
                // As absolute fallback, stop (should not happen unless buckets empty)
                out.warn(poolType + ": unable to fill remaining picks; bucket candidates empty.");
                break;
            }
        }

        if (!ordered) {
            Collections.sort(picks);
        }

        return picks;
    }

    private void pickFromTierWithFallback(
            PoolType poolType,
            Tier tier,
            int count,
            boolean allowRepeats,
            List<Integer> picks,
            TierBuckets buckets,
            Random rng,
            Map<Integer, Integer> usedCount,
            GeneratorOptions options,
            GeneratedBatch out
    ) {
        for (int i = 0; i < count; i++) {
            boolean ok = switch (tier) {
                case HOT -> pickOneWeighted(poolType, allowRepeats, picks, buckets.hot, rng, usedCount, options);
                case MID -> pickOneWeighted(poolType, allowRepeats, picks, buckets.mid, rng, usedCount, options);
                case COLD -> pickOneWeighted(poolType, allowRepeats, picks, buckets.cold, rng, usedCount, options);
            };

            if (ok) continue;

            // fall-down tier
            boolean fallbackOk = switch (tier) {
                case HOT -> pickOneWeighted(poolType, allowRepeats, picks, buckets.mid, rng, usedCount, options)
                        || pickOneWeighted(poolType, allowRepeats, picks, buckets.cold, rng, usedCount, options);
                case MID -> pickOneWeighted(poolType, allowRepeats, picks, buckets.cold, rng, usedCount, options);
                case COLD -> false;
            };

            if (!fallbackOk) {
                out.warn(poolType + ": tier " + tier + " empty after exclusions; unable to fill requested count.");
                return;
            }
        }
    }

    // -------------------------
    // GROUP WEIGHTED: PERCENT
    // -------------------------

    private List<Integer> groupWeightedPercent(
            PoolType poolType,
            int pickCount,
            boolean allowRepeats,
            boolean ordered,
            TicketGroup group,
            TierBuckets buckets,
            Random rng,
            Map<Integer, Integer> usedCount,
            GeneratorOptions options,
            GeneratedBatch out
    ) {
        int hotPct = clampPct(group.getHotPct());
        int midPct = clampPct(group.getMidPct());
        int coldPct = clampPct(group.getColdPct());

        if (hotPct + midPct + coldPct != 100) {
            out.warn(poolType + ": PERCENT group does not sum to 100; renormalizing.");
            int sum = hotPct + midPct + coldPct;
            if (sum <= 0) {
                // all zero => treat as all cold (quick pick behavior)
                out.warn(poolType + ": all percentages are 0; treating pool as quick pick (all cold).");
                return quickPickFallbackFromBuckets(poolType, pickCount, allowRepeats, ordered, buckets, rng, out);
            }
            // simple renormalize
            hotPct = (int) Math.round(hotPct * 100.0 / sum);
            midPct = (int) Math.round(midPct * 100.0 / sum);
            coldPct = 100 - hotPct - midPct;
        }

        List<Integer> picks = new ArrayList<>(pickCount);

        for (int i = 0; i < pickCount; i++) {
            Tier chosen = rollTier(hotPct, midPct, rng);

            // fall-down if chosen bucket empty
            boolean ok = switch (chosen) {
                case HOT -> pickOneWeighted(poolType, allowRepeats, picks, buckets.hot, rng, usedCount, options)
                        || pickOneWeighted(poolType, allowRepeats, picks, buckets.mid, rng, usedCount, options)
                        || pickOneWeighted(poolType, allowRepeats, picks, buckets.cold, rng, usedCount, options);
                case MID -> pickOneWeighted(poolType, allowRepeats, picks, buckets.mid, rng, usedCount, options)
                        || pickOneWeighted(poolType, allowRepeats, picks, buckets.cold, rng, usedCount, options);
                case COLD -> pickOneWeighted(poolType, allowRepeats, picks, buckets.cold, rng, usedCount, options);
            };

            if (!ok) {
                out.warn(poolType + ": no candidates available while picking by percent; treating pool as quick pick.");
                return quickPickFallbackFromBuckets(poolType, pickCount, allowRepeats, ordered, buckets, rng, out);
            }
        }

        if (!ordered) {
            Collections.sort(picks);
        }

        return picks;
    }

    private Tier rollTier(int hotPct, int midPct, Random rng) {
        int r = rng.nextInt(100); // 0..99
        if (r < hotPct) return Tier.HOT;
        if (r < hotPct + midPct) return Tier.MID;
        return Tier.COLD;
    }

    // -------------------------
    // Weighted pick core
    // -------------------------

    private boolean pickOneWeighted(
            PoolType poolType,
            boolean allowRepeats,
            List<Integer> currentPicks,
            List<NumberBall> candidates,
            Random rng,
            Map<Integer, Integer> usedCount,
            GeneratorOptions options
    ) {
        if (candidates == null || candidates.isEmpty()) return false;

        // If no repeats within ticket, remove already-picked values from consideration
        List<NumberBall> usable = candidates;
        if (!allowRepeats) {
            Set<Integer> already = new HashSet<>(currentPicks);
            usable = candidates.stream()
                    .filter(b -> !already.contains(b.getNumberValue()))
                    .toList();
            if (usable.isEmpty()) return false;
        }

        List<Double> weights = new ArrayList<>(usable.size());
        for (NumberBall b : usable) {
            double base = baseWeight(b);
            double temp = WeightedPicker.applyTemperature(base, options.temperature());
            int used = usedCount.getOrDefault(b.getNumberValue(), 0);
            double eff = WeightedPicker.applyDiminishingReturns(temp, used, options.alpha());
            weights.add(eff);
        }

        int idx = WeightedPicker.pickIndexByWeight(weights, rng);
        if (idx < 0) return false;

        int value = usable.get(idx).getNumberValue();
        currentPicks.add(value);

        // update per-batch usage
        usedCount.put(value, usedCount.getOrDefault(value, 0) + 1);

        return true;
    }

    /**
     * Default baseWeight:
     * - Always >0
     * - Uses tierCount and tier as a multiplier
     *
     * You can tune this later without changing generator structure.
     */
    private double baseWeight(NumberBall b) {
        int tierCount = (b.getTierCount() == null) ? 0 : b.getTierCount();
        double base = 1.0 + tierCount;

        Tier tier = b.getTier();
        if (tier == Tier.HOT) return base * 3.0;
        if (tier == Tier.MID) return base * 1.7;
        return base * 1.0;
    }

    private List<Integer> quickPickFallbackFromBuckets(
            PoolType poolType,
            int pickCount,
            boolean allowRepeats,
            boolean ordered,
            TierBuckets buckets,
            Random rng,
            GeneratedBatch out
    ) {
        List<Integer> all = new ArrayList<>();
        buckets.hot.forEach(b -> all.add(b.getNumberValue()));
        buckets.mid.forEach(b -> all.add(b.getNumberValue()));
        buckets.cold.forEach(b -> all.add(b.getNumberValue()));

        if (!allowRepeats && all.size() < pickCount) {
            out.warn(poolType + ": not enough candidates even after fallback; cannot satisfy pickCount.");
            return List.of();
        }

        List<Integer> picks = new ArrayList<>(pickCount);

        if (allowRepeats) {
            for (int i = 0; i < pickCount; i++) {
                picks.add(all.get(rng.nextInt(all.size())));
            }
        } else {
            Collections.shuffle(all, rng);
            picks.addAll(all.subList(0, pickCount));
        }

        if (!ordered) Collections.sort(picks);
        return picks;
    }

    // -------------------------
    // Bucketing helper
    // -------------------------

    private static final class TierBuckets {
        final List<NumberBall> hot;
        final List<NumberBall> mid;
        final List<NumberBall> cold;

        TierBuckets(List<NumberBall> hot, List<NumberBall> mid, List<NumberBall> cold) {
            this.hot = hot;
            this.mid = mid;
            this.cold = cold;
        }

        static TierBuckets fromNumberBalls(List<NumberBall> balls, Set<Integer> excluded) {
            if (balls == null) return new TierBuckets(List.of(), List.of(), List.of());

            List<NumberBall> filtered = balls.stream()
                    .filter(b -> b.getNumberValue() != null)
                    .filter(b -> excluded == null || !excluded.contains(b.getNumberValue()))
                    .toList();

            Map<Tier, List<NumberBall>> byTier = filtered.stream()
                    .collect(Collectors.groupingBy(b -> b.getTier() == null ? Tier.COLD : b.getTier()));

            return new TierBuckets(
                    byTier.getOrDefault(Tier.HOT, List.of()),
                    byTier.getOrDefault(Tier.MID, List.of()),
                    byTier.getOrDefault(Tier.COLD, List.of())
            );
        }
    }

    // -------------------------
    // tiny utils
    // -------------------------

    private int safeInt(Integer v) { return v == null ? 0 : v; }
    private boolean bool(Boolean v) { return v != null && v; }

    private int safeNullable(Integer v) { return v == null ? 0 : v; }

    private int clampPct(Integer v) {
        if (v == null) return 0;
        return Math.max(0, Math.min(100, v));
    }
}
