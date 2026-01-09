package com.lotteryapp.lottery.controller;

import com.lotteryapp.lottery.dto.common.ApiResponse;
import com.lotteryapp.lottery.dto.rules.request.CreateRulesRequest;
import com.lotteryapp.lottery.dto.rules.request.GetRulesDetailRequest;
import com.lotteryapp.lottery.dto.rules.request.UpdateRulesRequest;
import com.lotteryapp.lottery.dto.rules.response.RulesResponse;
import com.lotteryapp.lottery.service.RulesService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rules")
public class RulesController {

    private final RulesService rulesService;

    public RulesController(RulesService rulesService) {
        this.rulesService = rulesService;
    }

    @PostMapping("/create")
    public ApiResponse<RulesResponse> create(@Valid @RequestBody CreateRulesRequest request) {
        return rulesService.create(request);
    }

    @PostMapping("/update")
    public ApiResponse<RulesResponse> update(@Valid @RequestBody UpdateRulesRequest request) {
        return rulesService.update(request);
    }

    @PostMapping("/detail")
    public ApiResponse<RulesResponse> detail(@Valid @RequestBody GetRulesDetailRequest request) {
        return rulesService.detail(request);
    }
}
