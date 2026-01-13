package com.lotteryapp.lottery.controller;

import com.lotteryapp.lottery.dto.common.ApiResponse;
import com.lotteryapp.lottery.dto.numberball.response.NumberBallResponse;
import com.lotteryapp.lottery.dto.tier.request.GetTierListRequest;
import com.lotteryapp.lottery.dto.tier.request.GetTierMatrixRequest;
import com.lotteryapp.lottery.dto.tier.request.UpdateTierRangeRequest;
import com.lotteryapp.lottery.dto.tier.response.TierMatrixResponse;
import com.lotteryapp.lottery.dto.tier.response.UpdateTierRangeResponse;
import com.lotteryapp.lottery.service.TierService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/tier")
public class TierController {

    private final TierService tierService;

    @PostMapping("/get-all")
    public ApiResponse<TierMatrixResponse> getAll(@RequestBody GetTierMatrixRequest request) {
        return tierService.getTierMatrix(request);
    }

    @PostMapping("/get-one")
    public ApiResponse<List<NumberBallResponse>> getOne(@RequestBody GetTierListRequest request) {
        return tierService.getTierList(request);
    }

    @PostMapping("/update-range")
    public ApiResponse<UpdateTierRangeResponse> updateRange(@RequestBody UpdateTierRangeRequest request) {
        return tierService.updateTierRange(request);
    }
}
