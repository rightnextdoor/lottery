package com.lotteryapp.lottery.dto.ticketgen.response;

import java.util.List;

public record GeneratedPicksResponse(
        List<Integer> white,
        List<Integer> red
) {}
