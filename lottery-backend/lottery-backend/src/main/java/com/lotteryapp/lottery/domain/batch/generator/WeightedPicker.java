package com.lotteryapp.lottery.domain.batch.generator;

import java.util.*;

final class WeightedPicker {

    private WeightedPicker() {}

    /**
     * Picks one item index based on weights (all weights must be >= 0).
     * Returns -1 if total weight == 0 or list empty.
     */
    static int pickIndexByWeight(List<Double> weights, Random rng) {
        if (weights.isEmpty()) return -1;

        double total = 0.0;
        for (double w : weights) total += Math.max(0.0, w);
        if (total <= 0.0) return -1;

        double r = rng.nextDouble() * total;
        double acc = 0.0;
        for (int i = 0; i < weights.size(); i++) {
            acc += Math.max(0.0, weights.get(i));
            if (r <= acc) return i;
        }
        return weights.size() - 1;
    }

    static double applyTemperature(double baseWeight, double temperature) {
        if (baseWeight <= 0) return 0;
        // adjustedWeight = baseWeight^(1/temperature)
        return Math.pow(baseWeight, 1.0 / Math.max(0.0001, temperature));
    }

    static double applyDiminishingReturns(double adjustedWeight, int usedCount, double alpha) {
        return adjustedWeight / (1.0 + Math.max(0.0, alpha) * Math.max(0, usedCount));
    }
}
