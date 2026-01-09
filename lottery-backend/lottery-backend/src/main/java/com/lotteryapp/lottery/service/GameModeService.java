package com.lotteryapp.lottery.service;

import com.lotteryapp.common.exception.BadRequestException;
import com.lotteryapp.common.exception.NotFoundException;
import com.lotteryapp.lottery.domain.gamemode.*;
import com.lotteryapp.lottery.domain.jurisdiction.Jurisdiction;
import com.lotteryapp.lottery.dto.common.ApiResponse;
import com.lotteryapp.lottery.dto.common.PageResponse;
import com.lotteryapp.lottery.dto.gamemode.request.*;
import com.lotteryapp.lottery.dto.gamemode.response.GameModeResponse;
import com.lotteryapp.lottery.dto.gamemode.response.SearchGameModesResponse;
import com.lotteryapp.lottery.repository.GameModeRepository;
import com.lotteryapp.lottery.repository.JurisdictionRepository;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class GameModeService {

    private static final int MODE_KEY_MAX_LEN = 60;
    private static final Pattern NON_ALNUM = Pattern.compile("[^A-Z0-9]+");

    private final GameModeRepository gameModeRepository;
    private final JurisdictionRepository jurisdictionRepository;

    public GameModeService(GameModeRepository gameModeRepository, JurisdictionRepository jurisdictionRepository) {
        this.gameModeRepository = gameModeRepository;
        this.jurisdictionRepository = jurisdictionRepository;
    }

    @Transactional
    public ApiResponse<GameModeResponse> create(CreateGameModeRequest request) {
        if (request == null) throw new BadRequestException("Request body is required");

        String displayName = trimToNull(request.getDisplayName());
        if (displayName == null) throw new BadRequestException("displayName is required");
        if (request.getScope() == null) throw new BadRequestException("scope is required");

        if (request.getDrawDays() == null || request.getDrawDays().isEmpty()) {
            throw new BadRequestException("drawDays is required");
        }

        GameScope scope = request.getScope();
        Jurisdiction jurisdiction = resolveJurisdictionForCreate(scope, request.getJurisdictionCode());

        String modeKey = generateUniqueModeKey(displayName, scope, jurisdiction == null ? null : jurisdiction.getCode());

        GameMode gm = GameMode.builder()
                .modeKey(modeKey)
                .displayName(displayName)
                .scope(scope)
                .jurisdiction(jurisdiction)
                .drawDays(EnumSet.copyOf(request.getDrawDays()))
                .status(GameModeStatus.UP_TO_DATE)
                .build();

        GameMode saved = gameModeRepository.save(gm);
        return ApiResponse.ok("GameMode created", toResponse(saved));
    }

    @Transactional
    public ApiResponse<GameModeResponse> update(UpdateGameModeRequest request) {
        if (request == null) throw new BadRequestException("Request body is required");
        if (request.getId() == null) throw new BadRequestException("id is required");

        GameMode gm = gameModeRepository.findById(request.getId())
                .orElseThrow(() -> new NotFoundException("GameMode not found"));

        if (request.getDisplayName() != null) {
            String displayName = trimToNull(request.getDisplayName());
            if (displayName == null) throw new BadRequestException("displayName cannot be blank");
            gm.setDisplayName(displayName);
        }

        if (request.getDrawDays() != null) {
            if (request.getDrawDays().isEmpty()) {
                throw new BadRequestException("drawDays cannot be empty");
            }
            gm.setDrawDays(EnumSet.copyOf(request.getDrawDays()));
        }

        GameMode saved = gameModeRepository.save(gm);
        return ApiResponse.ok("GameMode updated", toResponse(saved));
    }

    @Transactional
    public ApiResponse<Void> delete(DeleteGameModeRequest request) {
        if (request == null) throw new BadRequestException("Request body is required");
        if (request.getId() == null) throw new BadRequestException("id is required");

        GameMode gm = gameModeRepository.findById(request.getId())
                .orElseThrow(() -> new NotFoundException("GameMode not found"));

        gameModeRepository.delete(gm);
        return ApiResponse.ok("GameMode deleted", null);
    }

    public ApiResponse<GameModeResponse> detail(GetGameModeDetailRequest request) {
        if (request == null) throw new BadRequestException("Request body is required");
        if (request.getId() == null) throw new BadRequestException("id is required");

        GameMode gm = gameModeRepository.findById(request.getId())
                .orElseThrow(() -> new NotFoundException("GameMode not found"));

        return ApiResponse.ok("GameMode loaded", toResponse(gm));
    }

    public ApiResponse<List<GameModeResponse>> listByState(ListGameModesByStateRequest request) {
        if (request == null) throw new BadRequestException("Request body is required");

        String stateCode = normalizeState(request.getStateCode());
        if (stateCode == null) throw new BadRequestException("stateCode is required");

        List<GameMode> modes = gameModeRepository.findAvailableForState(stateCode);
        List<GameModeResponse> items = modes.stream().map(this::toResponse).collect(Collectors.toList());
        return ApiResponse.ok("GameModes loaded", items);
    }

    public SearchGameModesResponse search(SearchGameModesRequest request) {
        if (request == null) request = new SearchGameModesRequest();

        int page = request.getPage();
        int size = request.getSize();
        if (page < 0) throw new BadRequestException("page must be >= 0");
        if (size < 1 || size > 200) throw new BadRequestException("size must be between 1 and 200");

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Order.asc("displayName")));

        String q = trimToNull(request.getQuery());
        String stateCode = normalizeState(request.getStateCode());

        Page<GameMode> result = gameModeRepository.search(q, request.getScope(), stateCode, pageable);

        List<GameModeResponse> items = result.getContent().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        PageResponse.PageMeta meta = PageResponse.PageMeta.builder()
                .pageNumber(result.getNumber())
                .pageSize(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .hasNext(result.hasNext())
                .hasPrevious(result.hasPrevious())
                .sort("displayName,asc")
                .build();

        PageResponse<GameModeResponse> pageResponse = PageResponse.<GameModeResponse>builder()
                .message("GameModes loaded")
                .items(items)
                .meta(meta)
                .build();

        return SearchGameModesResponse.builder()
                .results(pageResponse)
                .build();
    }

    // ---------------- helpers ----------------

    private Jurisdiction resolveJurisdictionForCreate(GameScope scope, String jurisdictionCode) {
        if (scope == GameScope.MULTI_STATE) {
            if (trimToNull(jurisdictionCode) != null) {
                throw new BadRequestException("jurisdictionCode must be null for MULTI_STATE");
            }
            return null;
        }

        // STATE_ONLY:
        String code = normalizeState(jurisdictionCode);
        if (code == null) throw new BadRequestException("jurisdictionCode is required for STATE_ONLY");

        Jurisdiction j = jurisdictionRepository.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new BadRequestException("Unknown jurisdictionCode"));

        if (!j.isEnabled()) {
            throw new BadRequestException("jurisdictionCode is disabled");
        }

        return j;
    }

    private GameModeResponse toResponse(GameMode gm) {
        return GameModeResponse.builder()
                .id(gm.getId())
                .modeKey(gm.getModeKey())
                .displayName(gm.getDisplayName())
                .scope(gm.getScope())
                .jurisdictionCode(gm.getJurisdiction() != null ? gm.getJurisdiction().getCode() : null)
                .rulesId(gm.getRules() != null ? gm.getRules().getId() : null)
                .tierRangeStartDate(gm.getTierRangeStartDate())
                .tierRangeEndDate(gm.getTierRangeEndDate())
                .drawDays(gm.getDrawDays() == null ? Collections.emptySet() : new HashSet<>(gm.getDrawDays()))
                .nextDrawDate(gm.getNextDrawDate())
                .drawTimeLocal(gm.getDrawTimeLocal())
                .drawTimeZoneId(gm.getDrawTimeZoneId())
                .latestDrawDate(gm.getLatestDrawDate())
                .latestWhiteWinningCsv(gm.getLatestWhiteWinningCsv())
                .latestRedWinningCsv(gm.getLatestRedWinningCsv())
                .latestJackpotAmount(gm.getLatestJackpotAmount())
                .latestCashValue(gm.getLatestCashValue())
                .status(gm.getStatus())
                .build();
    }

    private String generateUniqueModeKey(String displayName, GameScope scope, String jurisdictionCode) {
        String base = normalizeToModeKey(displayName);

        if (scope == GameScope.STATE_ONLY && jurisdictionCode != null && !jurisdictionCode.isBlank()) {
            String prefix = normalizeToModeKey(jurisdictionCode);
            if (!base.startsWith(prefix + "_")) {
                base = prefix + "_" + base;
            }
        }

        base = truncate(base, MODE_KEY_MAX_LEN);
        if (!gameModeRepository.existsByModeKeyIgnoreCase(base)) return base;

        // add suffix _2, _3, ...
        for (int i = 2; i <= 999; i++) {
            String suffix = "_" + i;
            String candidate = truncate(base, MODE_KEY_MAX_LEN - suffix.length()) + suffix;
            if (!gameModeRepository.existsByModeKeyIgnoreCase(candidate)) {
                return candidate;
            }
        }

        throw new BadRequestException("Unable to generate unique modeKey");
    }

    private static String normalizeToModeKey(String input) {
        String s = trimToNull(input);
        if (s == null) return "GAME";
        s = s.toUpperCase(Locale.ROOT);
        s = NON_ALNUM.matcher(s).replaceAll("_");
        s = s.replaceAll("_+", "_");
        s = s.replaceAll("^_+", "").replaceAll("_+$", "");
        if (s.isBlank()) return "GAME";
        return s;
    }

    private static String truncate(String s, int maxLen) {
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen);
    }

    private static String normalizeState(String stateCode) {
        String s = trimToNull(stateCode);
        return (s == null) ? null : s.toUpperCase(Locale.ROOT);
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
