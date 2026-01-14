package com.lotteryapp.lottery.controller;

import com.lotteryapp.lottery.dto.ticketgen.request.GenerateBatchRequest;
import com.lotteryapp.lottery.dto.ticketgen.response.GeneratedBatchResponse;
import com.lotteryapp.lottery.service.TicketGeneratorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ticket-generation")
public class TicketGenerationController {

    private final TicketGeneratorService ticketGeneratorService;

    @PostMapping("/generate")
    public GeneratedBatchResponse generate(@Valid @RequestBody GenerateBatchRequest request) {
        return ticketGeneratorService.generate(request);
    }
}
