package com.lotteryapp.lottery.service;

import com.lotteryapp.common.exception.BadRequestException;
import com.lotteryapp.common.exception.NotFoundException;
import com.lotteryapp.lottery.domain.batch.generator.SavedGeneratorSpec;
import com.lotteryapp.lottery.domain.gamemode.GameMode;
import com.lotteryapp.lottery.domain.group.TicketGroup;
import com.lotteryapp.lottery.domain.numbers.PoolType;
import com.lotteryapp.lottery.dto.common.ApiResponse;
import com.lotteryapp.lottery.dto.generatorspec.request.*;
import com.lotteryapp.lottery.dto.generatorspec.response.GeneratorSpecResponse;
import com.lotteryapp.lottery.repository.GameModeRepository;
import com.lotteryapp.lottery.repository.SavedGeneratorSpecRepository;
import com.lotteryapp.lottery.repository.TicketGroupRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GeneratorSpecService {

    private final SavedGeneratorSpecRepository specRepository;
    private final GameModeRepository gameModeRepository;
    private final TicketGroupRepository ticketGroupRepository;

    public GeneratorSpecService(
            SavedGeneratorSpecRepository specRepository,
            GameModeRepository gameModeRepository,
            TicketGroupRepository ticketGroupRepository
    ) {
        this.specRepository = specRepository;
        this.gameModeRepository = gameModeRepository;
        this.ticketGroupRepository = ticketGroupRepository;
    }

    @Transactional
    public ApiResponse<GeneratorSpecResponse> create(CreateGeneratorSpecRequest request) {
        if (request == null) throw new BadRequestException("Request body is required");
        Long gameModeId = requireGameModeId(request.getGameModeId());
        validateTicketCount(request.getTicketCount());

        GameMode gameMode = gameModeRepository.findById(gameModeId)
                .orElseThrow(() -> new NotFoundException("GameMode not found: " + gameModeId));

        validateGroupIds(gameModeId, request.getWhiteGroupId(), request.getRedGroupId());

        SavedGeneratorSpec spec = SavedGeneratorSpec.builder()
                .gameMode(gameMode)
                .ticketCount(request.getTicketCount())
                .whiteGroupId(request.getWhiteGroupId())
                .redGroupId(request.getRedGroupId())
                .excludeLastDrawNumbers(request.isExcludeLastDrawNumbers())
                .build();

        SavedGeneratorSpec saved = specRepository.save(spec);

        return ApiResponse.ok("Generator spec created", toResponse(saved));
    }

    @Transactional
    public ApiResponse<GeneratorSpecResponse> update(UpdateGeneratorSpecRequest request) {
        if (request == null) throw new BadRequestException("Request body is required");
        Long gameModeId = requireGameModeId(request.getGameModeId());
        Long id = requireId(request.getId());
        validateTicketCount(request.getTicketCount());

        validateGroupIds(gameModeId, request.getWhiteGroupId(), request.getRedGroupId());

        SavedGeneratorSpec spec = specRepository.findByIdAndGameModeId(id, gameModeId)
                .orElseThrow(() -> new NotFoundException("Generator spec not found: " + id));

        spec.setTicketCount(request.getTicketCount());
        spec.setWhiteGroupId(request.getWhiteGroupId());
        spec.setRedGroupId(request.getRedGroupId());
        spec.setExcludeLastDrawNumbers(request.isExcludeLastDrawNumbers());

        SavedGeneratorSpec saved = specRepository.save(spec);

        return ApiResponse.ok("Generator spec updated", toResponse(saved));
    }

    public ApiResponse<List<GeneratorSpecResponse>> list(ListGeneratorSpecsRequest request) {
        if (request == null) throw new BadRequestException("Request body is required");
        Long gameModeId = requireGameModeId(request.getGameModeId());

        // Ensure game exists (nice UX instead of silently returning empty)
        if (!gameModeRepository.existsById(gameModeId)) {
            throw new NotFoundException("GameMode not found: " + gameModeId);
        }

        List<GeneratorSpecResponse> items = specRepository.findByGameModeId(gameModeId)
                .stream()
                .map(this::toResponse)
                .toList();

        return ApiResponse.ok("Generator specs loaded", items);
    }

    @Transactional
    public ApiResponse<Void> delete(DeleteGeneratorSpecRequest request) {
        if (request == null) throw new BadRequestException("Request body is required");
        Long gameModeId = requireGameModeId(request.getGameModeId());
        Long id = requireId(request.getId());

        SavedGeneratorSpec spec = specRepository.findByIdAndGameModeId(id, gameModeId)
                .orElseThrow(() -> new NotFoundException("Generator spec not found: " + id));

        specRepository.delete(spec);

        return ApiResponse.ok("Generator spec deleted", null);
    }

    private void validateGroupIds(Long gameModeId, Long whiteGroupId, Long redGroupId) {
        if (whiteGroupId != null) {
            TicketGroup g = ticketGroupRepository.findById(whiteGroupId)
                    .orElseThrow(() -> new NotFoundException("TicketGroup not found: " + whiteGroupId));

            if (g.getGameMode() == null || g.getGameMode().getId() == null || !g.getGameMode().getId().equals(gameModeId)) {
                throw new BadRequestException("whiteGroupId must belong to the same gameModeId");
            }
            if (g.getPoolType() != PoolType.WHITE) {
                throw new BadRequestException("whiteGroupId must be a WHITE TicketGroup");
            }
        }

        if (redGroupId != null) {
            TicketGroup g = ticketGroupRepository.findById(redGroupId)
                    .orElseThrow(() -> new NotFoundException("TicketGroup not found: " + redGroupId));

            if (g.getGameMode() == null || g.getGameMode().getId() == null || !g.getGameMode().getId().equals(gameModeId)) {
                throw new BadRequestException("redGroupId must belong to the same gameModeId");
            }
            if (g.getPoolType() != PoolType.RED) {
                throw new BadRequestException("redGroupId must be a RED TicketGroup");
            }
        }
    }

    private GeneratorSpecResponse toResponse(SavedGeneratorSpec spec) {
        return GeneratorSpecResponse.builder()
                .id(spec.getId())
                .gameModeId(spec.getGameMode() != null ? spec.getGameMode().getId() : null)
                .ticketCount(spec.getTicketCount())
                .whiteGroupId(spec.getWhiteGroupId())
                .redGroupId(spec.getRedGroupId())
                .excludeLastDrawNumbers(spec.isExcludeLastDrawNumbers())
                .build();
    }

    private Long requireGameModeId(Long gameModeId) {
        if (gameModeId == null) throw new BadRequestException("gameModeId is required");
        return gameModeId;
    }

    private Long requireId(Long id) {
        if (id == null) throw new BadRequestException("id is required");
        return id;
    }

    private void validateTicketCount(int ticketCount) {
        if (ticketCount < 1) throw new BadRequestException("ticketCount must be >= 1");
    }
}
