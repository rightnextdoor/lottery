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
     * Generate preview-only tickets grouped by specResults.
     * No DB ids are produced here.
     */
    public GeneratedBatch generate(GeneratorContext ctx, List<GeneratorSpec> specs) {
        Objects.requireNonNull(ctx, "ctx");
        if (specs == null || specs.isEmpty()) throw new IllegalArgumentException("specs is required");

        Random rng = (ctx.options().randomSeed() == null)
                ? new Random()
                : new Random(ctx.options().randomSeed());

        GeneratedBatch out = new GeneratedBatch();

        for (int s = 0; s < specs.size(); s++) {
            int specNumber = s + 1;
            GeneratorSpec spec = specs.get(s);

            GeneratedSpecResult specOut = new GeneratedSpecResult(
                    specNumber,
                    spec.ticketCount(),
                    spec.whiteGroupId(),
                    spec.redGroupId(),
                    spec.excludeLastDrawNumbers()
            );

            // usedCounts resets PER SPEC (anti-dominance per spec)
            Map<PoolType, Map<Integer, Integer>> usedCounts = new EnumMap<>(PoolType.class);
            usedCounts.put(PoolType.WHITE, new HashMap<>());
            usedCounts.put(PoolType.RED, new HashMap<>());

            for (int t = 1; t <= spec.ticketCount(); t++) {
                List<Integer> whites = generatePool(ctx, spec, PoolType.WHITE, rng, usedCounts, specOut);
                List<Integer> reds = generatePool(ctx, spec, PoolType.RED, rng, usedCounts, specOut);

                specOut.addTicket(new GeneratedSpecTicket(
                        t,
                        new GeneratedPicks(whites, reds)
                ));
            }

            // Promote spec warnings to batch warnings (helps UI show “something happened”)
            if (!specOut.getWarnings().isEmpty()) {
                out.warn("Spec " + specNumber + ": one or more pools relaxed exclusions or fell back to quick pick.");
            }

            out.addSpecResult(specOut);
        }

        return out;
    }

    private List<Integer> generatePool(
            GeneratorContext ctx,
            GeneratorSpec spec,
            PoolType poolType,
            Random rng,
            Map<PoolType, Map<Integer, Integer>> usedCounts,
            GeneratedSpecResult specOut
    ) {
        Rules rules = ctx.rules();

        int min = (poolType == PoolType.WHITE) ? rules.getWhiteMin() : safeInt(rules.getRedMin());
        int max = (poolType == PoolType.WHITE) ? rules.getWhiteMax() : safeInt(rules.getRedMax());
        int pickCount = (poolType == PoolType.WHITE) ? rules.getWhitePickCount() : safeInt(rules.getRedPickCount());

        if (pickCount <= 0) return List.of();

        boolean ordered = (poolType == PoolType.WHITE) ? bool(rules.getWhiteOrdered()) : bool(rules.getRedOrdered());
        boolean allowRepeats = (poolType == PoolType.WHITE) ? bool(rules.getWhiteAllowRepeats()) : bool(rules.getRedAllowRepeats());

        Set<Integer> excluded = spec.excludedFor(poolType);

        // Decide per pool:
        // - if group missing => quick pick
        // - if group present => weighted for this pool
        TicketGroup group = spec.groupFor(poolType);
        if (group == null) {
            return quickPick(min, max, pickCount, allowRepeats, ordered, excluded, rng, specOut, poolType);
        }

        if (group.getPoolType() != poolType) {
            specOut.warn(poolType + ": group poolType mismatch; falling back to quick pick for this pool.");
            return quickPick(min, max, pickCount, allowRepeats, ordered, excluded, rng, specOut, poolType);
        }

        List<NumberBall> balls = (poolType == PoolType.WHITE) ? ctx.whiteBalls() : ctx.redBalls();
        if (balls == null) {
            specOut.warn(poolType + ": tier list missing; falling back to quick pick for this pool.");
            return quickPick(min, max, pickCount, allowRepeats, ordered, excluded, rng, specOut, poolType);
        }

        TierBuckets buckets = TierBuckets.fromNumberBalls(balls, excluded);

        // If Hot+Mid empty, treat as quick pick for this pool
        if (buckets.hot.isEmpty() && buckets.mid.isEmpty()) {
            specOut.warn(poolType + ": Hot+Mid empty; treating pool as quick pick (all cold).");
            return quickPick(min, max, pickCount, allowRepeats, ordered, excluded, rng, specOut, poolType);
        }

        if (group.getGroupMode() == GroupMode.COUNT) {
            return groupWeightedCount(
                    poolType, pickCount, allowRepeats, ordered,
                    group, buckets, rng, usedCounts.get(poolType), ctx.options(), specOut
            );
        }

        return groupWeightedPercent(
                poolType, pickCount, allowRepeats, ordered,
                group, buckets, rng, usedCounts.get(poolType), ctx.options(), specOut
        );
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
            GeneratedSpecResult specOut,
            PoolType poolType
    ) {
        List<Integer> candidates = new ArrayList<>();
        for (int v = min; v <= max; v++) {
            if (!excluded.contains(v)) candidates.add(v);
        }

        if (!allowRepeats && candidates.size() < pickCount) {
            specOut.warn(poolType + ": exclusions made quick pick impossible; relaxing exclusions for this pool.");
            candidates.clear();
            for (int v = min; v <= max; v++) candidates.add(v);
        }

        if (candidates.isEmpty()) {
            specOut.warn(poolType + ": no candidates available; relaxing exclusions for this pool.");
            for (int v = min; v <= max; v++) candidates.add(v);
        }

        List<Integer> picks = new ArrayList<>(pickCount);

        if (allowRepeats) {
            for (int i = 0; i < pickCount; i++) {
                picks.add(candidates.get(rng.nextInt(candidates.size())));
            }
        } else {
            Collections.shuffle(candidates, rng);
            picks.addAll(candidates.subList(0, pickCount));
        }

        // Sorting rule:
        // ordered=true => sort ascending
        if (ordered) {
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
            GeneratedSpecResult specOut
    ) {
        int hotN = safeNullable(group.getHotCount());
        int midN = safeNullable(group.getMidCount());
        int coldN = safeNullable(group.getColdCount());

        if (hotN + midN + coldN != pickCount) {
            specOut.warn(poolType + ": COUNT group does not match pickCount; attempting tier fall-down fill.");
        }

        List<Integer> picks = new ArrayList<>(pickCount);

        pickFromTierWithFallback(poolType, Tier.HOT, hotN, allowRepeats, picks, buckets, rng, usedCount, options, specOut);
        pickFromTierWithFallback(poolType, Tier.MID, midN, allowRepeats, picks, buckets, rng, usedCount, options, specOut);
        pickFromTierWithFallback(poolType, Tier.COLD, coldN, allowRepeats, picks, buckets, rng, usedCount, options, specOut);

        while (picks.size() < pickCount) {
            boolean ok = pickOneWeighted(poolType, allowRepeats, picks, buckets.hot, rng, usedCount, options)
                    || pickOneWeighted(poolType, allowRepeats, picks, buckets.mid, rng, usedCount, options)
                    || pickOneWeighted(poolType, allowRepeats, picks, buckets.cold, rng, usedCount, options);

            if (!ok) {
                specOut.warn(poolType + ": unable to fill remaining picks; bucket candidates empty.");
                break;
            }
        }

        if (ordered) Collections.sort(picks);
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
            GeneratedSpecResult specOut
    ) {
        for (int i = 0; i < count; i++) {
            boolean ok = switch (tier) {
                case HOT -> pickOneWeighted(poolType, allowRepeats, picks, buckets.hot, rng, usedCount, options);
                case MID -> pickOneWeighted(poolType, allowRepeats, picks, buckets.mid, rng, usedCount, options);
                case COLD -> pickOneWeighted(poolType, allowRepeats, picks, buckets.cold, rng, usedCount, options);
            };

            if (ok) continue;

            boolean fallbackOk = switch (tier) {
                case HOT -> pickOneWeighted(poolType, allowRepeats, picks, buckets.mid, rng, usedCount, options)
                        || pickOneWeighted(poolType, allowRepeats, picks, buckets.cold, rng, usedCount, options);
                case MID -> pickOneWeighted(poolType, allowRepeats, picks, buckets.cold, rng, usedCount, options);
                case COLD -> false;
            };

            if (!fallbackOk) {
                specOut.warn(poolType + ": tier " + tier + " empty after exclusions; unable to fill requested count.");
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
            GeneratedSpecResult specOut
    ) {
        int hotPct = clampPct(group.getHotPct());
        int midPct = clampPct(group.getMidPct());
        int coldPct = clampPct(group.getColdPct());

        if (hotPct + midPct + coldPct != 100) {
            specOut.warn(poolType + ": PERCENT group does not sum to 100; renormalizing.");
            int sum = hotPct + midPct + coldPct;
            if (sum <= 0) {
                specOut.warn(poolType + ": all percentages are 0; treating pool as quick pick (all cold).");
                return quickPickFallbackFromBuckets(poolType, pickCount, allowRepeats, ordered, buckets, rng, specOut);
            }
            hotPct = (int) Math.round(hotPct * 100.0 / sum);
            midPct = (int) Math.round(midPct * 100.0 / sum);
            coldPct = 100 - hotPct - midPct;
        }

        List<Integer> picks = new ArrayList<>(pickCount);

        for (int i = 0; i < pickCount; i++) {
            Tier chosen = rollTier(hotPct, midPct, rng);

            boolean ok = switch (chosen) {
                case HOT -> pickOneWeighted(poolType, allowRepeats, picks, buckets.hot, rng, usedCount, options)
                        || pickOneWeighted(poolType, allowRepeats, picks, buckets.mid, rng, usedCount, options)
                        || pickOneWeighted(poolType, allowRepeats, picks, buckets.cold, rng, usedCount, options);
                case MID -> pickOneWeighted(poolType, allowRepeats, picks, buckets.mid, rng, usedCount, options)
                        || pickOneWeighted(poolType, allowRepeats, picks, buckets.cold, rng, usedCount, options);
                case COLD -> pickOneWeighted(poolType, allowRepeats, picks, buckets.cold, rng, usedCount, options);
            };

            if (!ok) {
                specOut.warn(poolType + ": no candidates available while picking by percent; treating pool as quick pick.");
                return quickPickFallbackFromBuckets(poolType, pickCount, allowRepeats, ordered, buckets, rng, specOut);
            }
        }

        if (ordered) Collections.sort(picks);
        return picks;
    }

    private Tier rollTier(int hotPct, int midPct, Random rng) {
        int r = rng.nextInt(100);
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

        usedCount.put(value, usedCount.getOrDefault(value, 0) + 1);
        return true;
    }

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
            GeneratedSpecResult specOut
    ) {
        List<Integer> all = new ArrayList<>();
        buckets.hot.forEach(b -> all.add(b.getNumberValue()));
        buckets.mid.forEach(b -> all.add(b.getNumberValue()));
        buckets.cold.forEach(b -> all.add(b.getNumberValue()));

        if (!allowRepeats && all.size() < pickCount) {
            specOut.warn(poolType + ": not enough candidates even after fallback; cannot satisfy pickCount.");
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

        if (ordered) Collections.sort(picks);
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

    private int safeInt(Integer v) { return v == null ? 0 : v; }
    private boolean bool(Boolean v) { return v != null && v; }
    private int safeNullable(Integer v) { return v == null ? 0 : v; }

    private int clampPct(Integer v) {
        if (v == null) return 0;
        return Math.max(0, Math.min(100, v));
    }
}
