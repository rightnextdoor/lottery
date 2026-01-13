package com.lotteryapp.lottery.service;

import com.lotteryapp.common.exception.BadRequestException;
import com.lotteryapp.common.exception.NotFoundException;
import com.lotteryapp.lottery.domain.gamemode.GameMode;
import com.lotteryapp.lottery.domain.group.GroupMode;
import com.lotteryapp.lottery.domain.group.TicketGroup;
import com.lotteryapp.lottery.dto.group.request.*;
import com.lotteryapp.lottery.dto.group.response.*;

import com.lotteryapp.lottery.repository.GameModeRepository;
import com.lotteryapp.lottery.repository.TicketGroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TicketGroupService {

    private final TicketGroupRepository ticketGroupRepository;
    private final GameModeRepository gameModeRepository;

    @Transactional(readOnly = true)
    public GetTicketGroupsResponse getAllForGame(GetTicketGroupsRequest request) {
        Long gameModeId = request.getGameModeId();
        if (gameModeId == null) throw new BadRequestException("gameModeId is required");

        if (!gameModeRepository.existsById(gameModeId)) {
            throw new NotFoundException("GameMode not found: " + gameModeId);
        }

        List<TicketGroupResponse> groups = ticketGroupRepository
                .findAllByGameMode_IdOrderByIdAsc(gameModeId)
                .stream()
                .map(this::toResponse)
                .toList();

        return GetTicketGroupsResponse.builder()
                .groups(groups)
                .build();
    }

    @Transactional(readOnly = true)
    public GetTicketGroupResponse getById(GetTicketGroupRequest request) {
        Long groupId = request.getGroupId();
        if (groupId == null) throw new BadRequestException("groupId is required");

        TicketGroup group = ticketGroupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException("TicketGroup not found: " + groupId));

        return GetTicketGroupResponse.builder()
                .group(toResponse(group))
                .build();
    }

    @Transactional
    public CreateTicketGroupResponse create(CreateTicketGroupRequest request) {
        validateCreateRequestBasics(request);

        GameMode gameMode = gameModeRepository.findById(request.getGameModeId())
                .orElseThrow(() -> new NotFoundException("GameMode not found: " + request.getGameModeId()));

        TicketGroup group = TicketGroup.builder()
                .gameMode(gameMode)
                .poolType(request.getPoolType())
                .groupMode(request.getGroupMode())
                .build();

        applyTierMixForCreate(group, request);

        String gameModeName = resolveGameModeName(gameMode);
        String displayName = computeDisplayName(gameModeName, group);
        String groupKey = computeGroupKey(gameModeName, group);

        assertNoDuplicatesOnCreate(group, groupKey, displayName);

        group.setDisplayName(displayName);
        group.setGroupKey(groupKey);

        TicketGroup saved = ticketGroupRepository.save(group);

        return CreateTicketGroupResponse.builder()
                .group(toResponse(saved))
                .build();
    }

    @Transactional
    public UpdateTicketGroupResponse update(UpdateTicketGroupRequest request) {
        Long groupId = request.getGroupId();
        if (groupId == null) throw new BadRequestException("groupId is required");

        TicketGroup group = ticketGroupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException("TicketGroup not found: " + groupId));

        applyTierMixForUpdate(group, request);

        GameMode gameMode = group.getGameMode();
        String gameModeName = resolveGameModeName(gameMode);

        String newDisplayName = computeDisplayName(gameModeName, group);
        String newGroupKey = computeGroupKey(gameModeName, group);

        assertNoDuplicatesOnUpdate(group, groupId, newGroupKey, newDisplayName);

        group.setDisplayName(newDisplayName);
        group.setGroupKey(newGroupKey);

        TicketGroup saved = ticketGroupRepository.save(group);

        return UpdateTicketGroupResponse.builder()
                .group(toResponse(saved))
                .build();
    }

    @Transactional
    public DeleteTicketGroupResponse delete(DeleteTicketGroupRequest request) {
        Long groupId = request.getGroupId();
        if (groupId == null) throw new BadRequestException("groupId is required");

        if (!ticketGroupRepository.existsById(groupId)) {
            throw new NotFoundException("TicketGroup not found: " + groupId);
        }

        ticketGroupRepository.deleteById(groupId);

        return DeleteTicketGroupResponse.builder()
                .deletedGroupId(groupId)
                .build();
    }

    // ---------------------------
    // Validation + Mix Application
    // ---------------------------

    private void validateCreateRequestBasics(CreateTicketGroupRequest request) {
        if (request.getGameModeId() == null) throw new BadRequestException("gameModeId is required");
        if (request.getPoolType() == null) throw new BadRequestException("poolType is required");
        if (request.getGroupMode() == null) throw new BadRequestException("groupMode is required");
    }

    private void applyTierMixForCreate(TicketGroup group, CreateTicketGroupRequest request) {
        if (request.getGroupMode() == GroupMode.COUNT) {
            Integer hot = requireNonNegative(request.getHotCount(), "hotCount");
            Integer mid = requireNonNegative(request.getMidCount(), "midCount");
            Integer cold = requireNonNegative(request.getColdCount(), "coldCount");

            group.setHotCount(hot);
            group.setMidCount(mid);
            group.setColdCount(cold);

            // clear pct fields
            group.setHotPct(null);
            group.setMidPct(null);
            group.setColdPct(null);
            return;
        }

        if (request.getGroupMode() == GroupMode.PERCENT) {
            Integer hot = requirePercent(request.getHotPct(), "hotPct");
            Integer mid = requirePercent(request.getMidPct(), "midPct");
            Integer cold = requirePercent(request.getColdPct(), "coldPct");

            if (hot + mid + cold != 100) {
                throw new BadRequestException("Percent groups must sum to 100");
            }

            group.setHotPct(hot);
            group.setMidPct(mid);
            group.setColdPct(cold);

            // clear count fields
            group.setHotCount(null);
            group.setMidCount(null);
            group.setColdCount(null);
            return;
        }

        throw new BadRequestException("Unsupported groupMode: " + request.getGroupMode());
    }

    private void applyTierMixForUpdate(TicketGroup group, UpdateTicketGroupRequest request) {
        if (group.isCountMode()) {
            Integer hot = requireNonNegative(request.getHotCount(), "hotCount");
            Integer mid = requireNonNegative(request.getMidCount(), "midCount");
            Integer cold = requireNonNegative(request.getColdCount(), "coldCount");

            group.setHotCount(hot);
            group.setMidCount(mid);
            group.setColdCount(cold);

            // keep pct cleared
            group.setHotPct(null);
            group.setMidPct(null);
            group.setColdPct(null);
            return;
        }

        if (group.isPercentMode()) {
            Integer hot = requirePercent(request.getHotPct(), "hotPct");
            Integer mid = requirePercent(request.getMidPct(), "midPct");
            Integer cold = requirePercent(request.getColdPct(), "coldPct");

            if (hot + mid + cold != 100) {
                throw new BadRequestException("Percent groups must sum to 100");
            }

            group.setHotPct(hot);
            group.setMidPct(mid);
            group.setColdPct(cold);

            // keep counts cleared
            group.setHotCount(null);
            group.setMidCount(null);
            group.setColdCount(null);
            return;
        }

        throw new BadRequestException("Unsupported groupMode: " + group.getGroupMode());
    }

    private Integer requireNonNegative(Integer value, String field) {
        if (value == null) throw new BadRequestException(field + " is required");
        if (value < 0) throw new BadRequestException(field + " must be >= 0");
        return value;
    }

    private Integer requirePercent(Integer value, String field) {
        if (value == null) throw new BadRequestException(field + " is required");
        if (value < 0 || value > 100) throw new BadRequestException(field + " must be between 0 and 100");
        return value;
    }

    // ---------------------------
    // Duplicate checks
    // ---------------------------

    private void assertNoDuplicatesOnCreate(TicketGroup group, String groupKey, String displayName) {
        boolean exists;
        if (group.isCountMode()) {
            exists = ticketGroupRepository.existsByGameMode_IdAndPoolTypeAndGroupModeAndHotCountAndMidCountAndColdCount(
                    group.getGameMode().getId(),
                    group.getPoolType(),
                    group.getGroupMode(),
                    group.getHotCount(),
                    group.getMidCount(),
                    group.getColdCount()
            );
        } else {
            exists = ticketGroupRepository.existsByGameMode_IdAndPoolTypeAndGroupModeAndHotPctAndMidPctAndColdPct(
                    group.getGameMode().getId(),
                    group.getPoolType(),
                    group.getGroupMode(),
                    group.getHotPct(),
                    group.getMidPct(),
                    group.getColdPct()
            );
        }

        if (exists) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("groupKey", groupKey);
            details.put("displayName", displayName);
            throw new BadRequestException("Duplicate group already exists", "DUPLICATE_GROUP", details);
        }
    }

    private void assertNoDuplicatesOnUpdate(TicketGroup group, Long groupId, String groupKey, String displayName) {
        boolean exists;
        if (group.isCountMode()) {
            exists = ticketGroupRepository.existsByGameMode_IdAndPoolTypeAndGroupModeAndHotCountAndMidCountAndColdCountAndIdNot(
                    group.getGameMode().getId(),
                    group.getPoolType(),
                    group.getGroupMode(),
                    group.getHotCount(),
                    group.getMidCount(),
                    group.getColdCount(),
                    groupId
            );
        } else {
            exists = ticketGroupRepository.existsByGameMode_IdAndPoolTypeAndGroupModeAndHotPctAndMidPctAndColdPctAndIdNot(
                    group.getGameMode().getId(),
                    group.getPoolType(),
                    group.getGroupMode(),
                    group.getHotPct(),
                    group.getMidPct(),
                    group.getColdPct(),
                    groupId
            );
        }

        if (exists) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("groupKey", groupKey);
            details.put("displayName", displayName);
            throw new BadRequestException("Duplicate group already exists", "DUPLICATE_GROUP", details);
        }
    }

    // ---------------------------
    // Naming helpers
    // ---------------------------

    private String resolveGameModeName(GameMode gameMode) {
        // Adjust if your GameMode getter differs (displayName vs name)
        String name = gameMode.getDisplayName();
        if (name == null || name.isBlank()) {
            return "GAMEMODE_" + gameMode.getId();
        }
        return name.trim();
    }

    private String computeDisplayName(String gameModeName, TicketGroup group) {
        String pool = String.valueOf(group.getPoolType());
        if (group.isCountMode()) {
            return gameModeName + " " + pool
                    + " Hot " + group.getHotCount()
                    + " Mid " + group.getMidCount()
                    + " Cold " + group.getColdCount();
        }
        return gameModeName + " " + pool
                + " Hot " + group.getHotPct()
                + " Mid " + group.getMidPct()
                + " Cold " + group.getColdPct();
    }

    private String computeGroupKey(String gameModeName, TicketGroup group) {
        String base = gameModeName.trim()
                .toUpperCase()
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");

        String pool = String.valueOf(group.getPoolType()).toUpperCase();

        if (group.isCountMode()) {
            return base + "_" + pool
                    + "_HOT_" + group.getHotCount()
                    + "_MID_" + group.getMidCount()
                    + "_COLD_" + group.getColdCount();
        }

        return base + "_" + pool
                + "_HOT_" + group.getHotPct()
                + "_MID_" + group.getMidPct()
                + "_COLD_" + group.getColdPct();
    }

    // ---------------------------
    // Mapping
    // ---------------------------

    private TicketGroupResponse toResponse(TicketGroup g) {
        return TicketGroupResponse.builder()
                .id(g.getId())
                .gameModeId(g.getGameMode() != null ? g.getGameMode().getId() : null)
                .poolType(g.getPoolType())
                .groupMode(g.getGroupMode())
                .groupKey(g.getGroupKey())
                .displayName(g.getDisplayName())
                .hotCount(g.getHotCount())
                .midCount(g.getMidCount())
                .coldCount(g.getColdCount())
                .hotPct(g.getHotPct())
                .midPct(g.getMidPct())
                .coldPct(g.getColdPct())
                .createdAt(g.getCreatedAt())
                .updatedAt(g.getUpdatedAt())
                .build();
    }
}
