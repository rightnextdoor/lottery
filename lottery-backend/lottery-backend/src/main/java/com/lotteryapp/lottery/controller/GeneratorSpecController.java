package com.lotteryapp.lottery.controller;

import com.lotteryapp.lottery.dto.common.ApiResponse;
import com.lotteryapp.lottery.dto.generatorspec.request.*;
import com.lotteryapp.lottery.dto.generatorspec.response.GeneratorSpecResponse;
import com.lotteryapp.lottery.service.GeneratorSpecService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/generator-specs")
public class GeneratorSpecController {

    private final GeneratorSpecService generatorSpecService;

    public GeneratorSpecController(GeneratorSpecService generatorSpecService) {
        this.generatorSpecService = generatorSpecService;
    }

    @PostMapping("/create")
    public ApiResponse<GeneratorSpecResponse> create(@RequestBody CreateGeneratorSpecRequest request) {
        return generatorSpecService.create(request);
    }

    @PostMapping("/update")
    public ApiResponse<GeneratorSpecResponse> update(@RequestBody UpdateGeneratorSpecRequest request) {
        return generatorSpecService.update(request);
    }

    @PostMapping("/list")
    public ApiResponse<List<GeneratorSpecResponse>> list(@RequestBody ListGeneratorSpecsRequest request) {
        return generatorSpecService.list(request);
    }

    @PostMapping("/delete")
    public ApiResponse<Void> delete(@RequestBody DeleteGeneratorSpecRequest request) {
        return generatorSpecService.delete(request);
    }
}
