package com.lotteryapp.lottery.controller;

import com.lotteryapp.lottery.dto.batch.request.*;
import com.lotteryapp.lottery.dto.batch.response.BatchCheckResponse;
import com.lotteryapp.lottery.dto.batch.response.SavedBatchResponse;
import com.lotteryapp.lottery.service.BatchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/batches")
public class BatchController {

    private final BatchService batchService;

    @PostMapping("/save")
    public SavedBatchResponse save(@Valid @RequestBody SaveBatchRequest request) {
        return batchService.saveBatch(request);
    }

    @PostMapping("/list")
    public Page<SavedBatchResponse> list(@Valid @RequestBody ListBatchesRequest request) {
        return batchService.listBatches(request);
    }

    @PostMapping("/detail")
    public SavedBatchResponse detail(@Valid @RequestBody GetBatchDetailRequest request) {
        return batchService.getBatchDetail(request);
    }

    @PostMapping("/delete")
    public void delete(@Valid @RequestBody DeleteBatchRequest request) {
        batchService.deleteBatch(request);
    }

    @PostMapping("/keep-forever")
    public SavedBatchResponse keepForever(@Valid @RequestBody UpdateKeepForeverRequest request) {
        return batchService.updateKeepForever(request);
    }

    @PostMapping("/check")
    public BatchCheckResponse check(@Valid @RequestBody BatchCheckRequest request) {
        return batchService.checkBatch(request);
    }
}
