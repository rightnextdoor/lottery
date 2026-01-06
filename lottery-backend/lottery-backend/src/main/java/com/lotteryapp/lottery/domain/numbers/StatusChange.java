package com.lotteryapp.lottery.domain.numbers;

/**
 * Shows how a number moved after the most recent tier recalculation.
 * NONE = "blank" (no change).
 */
public enum StatusChange {
    PROMOTED,
    DEMOTED,
    NONE
}
