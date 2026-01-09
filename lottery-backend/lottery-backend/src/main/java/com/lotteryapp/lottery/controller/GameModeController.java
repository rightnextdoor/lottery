package com.lotteryapp.lottery.controller;

import com.lotteryapp.lottery.dto.common.ApiResponse;
import com.lotteryapp.lottery.dto.gamemode.request.*;
import com.lotteryapp.lottery.dto.gamemode.response.GameModeResponse;
import com.lotteryapp.lottery.dto.gamemode.response.SearchGameModesResponse;
import com.lotteryapp.lottery.service.GameModeService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/game-modes")
public class GameModeController {

    private final GameModeService gameModeService;

    public GameModeController(GameModeService gameModeService) {
        this.gameModeService = gameModeService;
    }

    @PostMapping("/search")
    public SearchGameModesResponse search(@RequestBody(required = false) SearchGameModesRequest request) {
        return gameModeService.search(request);
    }

    @PostMapping("/by-state")
    public ApiResponse<List<GameModeResponse>> listByState(@Valid @RequestBody ListGameModesByStateRequest request) {
        return gameModeService.listByState(request);
    }

    @PostMapping("/detail")
    public ApiResponse<GameModeResponse> detail(@Valid @RequestBody GetGameModeDetailRequest request) {
        return gameModeService.detail(request);
    }

    @PostMapping("/create")
    public ApiResponse<GameModeResponse> create(@Valid @RequestBody CreateGameModeRequest request) {
        return gameModeService.create(request);
    }

    @PostMapping("/update")
    public ApiResponse<GameModeResponse> update(@Valid @RequestBody UpdateGameModeRequest request) {
        return gameModeService.update(request);
    }

    @PostMapping("/delete")
    public ApiResponse<Void> delete(@Valid @RequestBody DeleteGameModeRequest request) {
        return gameModeService.delete(request);
    }
}
