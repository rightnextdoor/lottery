package com.lotteryapp.lottery.application.numbers;

import com.lotteryapp.lottery.domain.gamemode.GameMode;
import com.lotteryapp.lottery.domain.gamemode.Rules;
import com.lotteryapp.lottery.domain.numbers.tier.TierWindow;

import java.time.LocalDate;

public final class GameModeTierWindowResolver {

    private GameModeTierWindowResolver() {}

    /**
     * Resolves the effective tier window from GameMode.tierRangeStartDate/tierRangeEndDate.
     * Defaults (when nulls present) follow the planning notes:
     * - both null -> history (Rules.formatStartDate .. today)
     * - start only -> end = today
     * - end only -> start = Rules.formatStartDate (or earliest stored draw; service can override)
     */
    public static TierWindow resolve(GameMode mode, LocalDate today) {
        Rules rules = mode.getRules();
        if (rules == null) {
            throw new IllegalStateException("GameMode.rules is required to resolve tier window defaults.");
        }

        LocalDate start = mode.getTierRangeStartDate();
        LocalDate end = mode.getTierRangeEndDate();

        if (start == null && end == null) {
            start = rules.getFormatStartDate() != null ? rules.getFormatStartDate() : LocalDate.MIN;
            end = today;
            return new TierWindow(start, end);
        }

        if (start != null && end == null) {
            end = today;
            return new TierWindow(start, end);
        }

        if (start == null) {
            start = rules.getFormatStartDate() != null ? rules.getFormatStartDate() : LocalDate.MIN;
            return new TierWindow(start, end);
        }

        return new TierWindow(start, end);
    }
}
