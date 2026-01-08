package com.lotteryapp.lottery.domain.batch.generator;

import java.util.ArrayList;
import java.util.List;

public class GeneratedBatch {

    private final List<GeneratedTicket> tickets = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    public List<GeneratedTicket> getTickets() {
        return tickets;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void addTicket(GeneratedTicket ticket) {
        tickets.add(ticket);
    }

    public void warn(String message) {
        warnings.add(message);
    }
}
