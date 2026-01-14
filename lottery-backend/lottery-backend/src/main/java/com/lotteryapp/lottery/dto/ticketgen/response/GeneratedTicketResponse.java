package com.lotteryapp.lottery.dto.ticketgen.response;

public record GeneratedTicketResponse(
        int ticketNumber,
        GeneratedPicksResponse picks
) {}
