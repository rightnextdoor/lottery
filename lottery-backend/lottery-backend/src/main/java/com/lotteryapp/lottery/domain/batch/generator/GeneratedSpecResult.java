package com.lotteryapp.lottery.domain.batch.generator;

import java.util.ArrayList;
import java.util.List;

public class GeneratedSpecResult {

    private final int specNumber;
    private final int ticketCount;

    private final Long whiteGroupId;
    private final Long redGroupId;

    private final boolean excludeLastDrawNumbers;

    private final List<GeneratedSpecTicket> tickets = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    public GeneratedSpecResult(
            int specNumber,
            int ticketCount,
            Long whiteGroupId,
            Long redGroupId,
            boolean excludeLastDrawNumbers
    ) {
        this.specNumber = specNumber;
        this.ticketCount = ticketCount;
        this.whiteGroupId = whiteGroupId;
        this.redGroupId = redGroupId;
        this.excludeLastDrawNumbers = excludeLastDrawNumbers;
    }

    public int getSpecNumber() { return specNumber; }
    public int getTicketCount() { return ticketCount; }
    public Long getWhiteGroupId() { return whiteGroupId; }
    public Long getRedGroupId() { return redGroupId; }
    public boolean isExcludeLastDrawNumbers() { return excludeLastDrawNumbers; }

    public List<GeneratedSpecTicket> getTickets() { return tickets; }
    public List<String> getWarnings() { return warnings; }

    public void addTicket(GeneratedSpecTicket ticket) {
        tickets.add(ticket);
    }

    public void warn(String message) {
        warnings.add(message);
    }
}
