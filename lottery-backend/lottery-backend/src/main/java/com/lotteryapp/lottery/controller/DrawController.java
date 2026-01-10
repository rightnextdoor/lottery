package com.lotteryapp.lottery.controller;

import com.lotteryapp.lottery.dto.common.ApiResponse;
import com.lotteryapp.lottery.dto.common.PageResponse;
import com.lotteryapp.lottery.dto.draw.request.*;
import com.lotteryapp.lottery.dto.draw.response.*;
import com.lotteryapp.lottery.service.DrawService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/draws")
public class DrawController {

    private final DrawService drawService;

    public DrawController(DrawService drawService) {
        this.drawService = drawService;
    }

    @PostMapping("/latest")
    public ApiResponse<DrawBundleResponse> latest(@Valid @RequestBody GetLatestDrawRequest request) {
        return drawService.getLatest(request);
    }

    @PostMapping("/last5")
    public ApiResponse<DrawBundleResponse> last5(@Valid @RequestBody GetLastDrawsRequest request) {
        return drawService.getLast5(request);
    }

    @PostMapping("/by-date")
    public ApiResponse<DrawBundleResponse> byDate(@Valid @RequestBody GetDrawByDateRequest request) {
        return drawService.getByDate(request);
    }

    @PostMapping("/current-format")
    public ApiResponse<DrawBundleResponse> currentFormat(@Valid @RequestBody GetCurrentFormatDrawsRequest request) {
        return drawService.getCurrentFormat(request);
    }

    @PostMapping("/schedule")
    public ApiResponse<DrawScheduleResponse> schedule(@Valid @RequestBody GetDrawScheduleRequest request) {
        return drawService.getSchedule(request);
    }

    @PostMapping("/sync-status")
    public ApiResponse<DrawSyncStatusResponse> syncStatus(@Valid @RequestBody GetDrawSyncStatusRequest request) {
        return drawService.getSyncStatus(request);
    }

    @PostMapping("/upsert")
    public ApiResponse<DrawBundleResponse> upsert(@Valid @RequestBody UpsertDrawRequest request) {
        return drawService.upsert(request);
    }

    @PostMapping("/conflicts/list")
    public PageResponse<DrawConflictResponse> listConflicts(@Valid @RequestBody ListDrawConflictsRequest request) {
        return drawService.listConflicts(request);
    }

    @PostMapping("/conflicts/acknowledge")
    public ApiResponse<Void> acknowledge(@Valid @RequestBody AcknowledgeDrawConflictRequest request) {
        return drawService.acknowledgeConflict(request);
    }

    @PostMapping("/conflicts/resolve")
    public ApiResponse<Void> resolve(@Valid @RequestBody ResolveDrawConflictRequest request) {
        return drawService.resolveConflict(request);
    }
}
