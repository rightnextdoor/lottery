package com.lotteryapp.lottery.controller;

import com.lotteryapp.lottery.dto.common.ApiResponse;
import com.lotteryapp.lottery.dto.numberball.request.GetNumberBallDetailRequest;
import com.lotteryapp.lottery.dto.numberball.request.GetNumberBallsRequest;
import com.lotteryapp.lottery.dto.numberball.response.GetNumberBallsResponse;
import com.lotteryapp.lottery.dto.numberball.response.NumberBallResponse;
import com.lotteryapp.lottery.service.NumberBallService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/numberball")
public class NumberBallController {

    private final NumberBallService numberBallService;

    @PostMapping("/get-all")
    public ApiResponse<GetNumberBallsResponse> getAll(@RequestBody GetNumberBallsRequest request) {
        return numberBallService.getNumberBalls(request);
    }

    @PostMapping("/get-one")
    public ApiResponse<NumberBallResponse> getOne(@RequestBody GetNumberBallDetailRequest request) {
        return numberBallService.getNumberBallDetail(request);
    }
}
