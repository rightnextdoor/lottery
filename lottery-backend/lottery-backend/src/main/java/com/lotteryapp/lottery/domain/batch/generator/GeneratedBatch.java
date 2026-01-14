package com.lotteryapp.lottery.domain.batch.generator;

import java.util.ArrayList;
import java.util.List;

public class GeneratedBatch {

    private final List<GeneratedSpecResult> specResults = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    public List<GeneratedSpecResult> getSpecResults() {
        return specResults;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void addSpecResult(GeneratedSpecResult specResult) {
        specResults.add(specResult);
    }

    public void warn(String message) {
        warnings.add(message);
    }
}
