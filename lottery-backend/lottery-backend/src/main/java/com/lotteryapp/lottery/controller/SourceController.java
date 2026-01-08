package com.lotteryapp.lottery.controller;

import com.lotteryapp.lottery.dto.common.ApiResponse;
import com.lotteryapp.lottery.dto.common.PageResponse;
import com.lotteryapp.lottery.dto.source.request.CreateSourceRequest;
import com.lotteryapp.lottery.dto.source.request.DeleteSourceRequest;
import com.lotteryapp.lottery.dto.source.request.ListSourcesRequest;
import com.lotteryapp.lottery.dto.source.request.UpdateSourceRequest;
import com.lotteryapp.lottery.dto.source.response.SourceResponse;
import com.lotteryapp.lottery.service.SourceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sources")
public class SourceController {

    private final SourceService sourceService;

    public SourceController(SourceService sourceService) {
        this.sourceService = sourceService;
    }

    @PostMapping("/search")
    public PageResponse<SourceResponse> list(@RequestBody(required = false) ListSourcesRequest request) {
        return sourceService.list(request);
    }

    @PostMapping
    public ApiResponse<SourceResponse> create(@Valid @RequestBody CreateSourceRequest request) {
        return sourceService.create(request);
    }

    @PutMapping("/{sourceId}")
    public ApiResponse<SourceResponse> update(@PathVariable Long sourceId, @Valid @RequestBody UpdateSourceRequest request) {
        return sourceService.update(sourceId, request);
    }

    @PostMapping("/delete")
    public ApiResponse<Void> delete(@Valid @RequestBody DeleteSourceRequest request) {
        return sourceService.delete(request);
    }
}
