package com.lotteryapp.lottery.service;

import com.lotteryapp.common.exception.BadRequestException;
import com.lotteryapp.common.exception.NotFoundException;
import com.lotteryapp.lottery.domain.source.Source;
import com.lotteryapp.lottery.dto.common.ApiResponse;
import com.lotteryapp.lottery.dto.common.PageResponse;
import com.lotteryapp.lottery.dto.source.request.CreateSourceRequest;
import com.lotteryapp.lottery.dto.source.request.DeleteSourceRequest;
import com.lotteryapp.lottery.dto.source.request.ListSourcesRequest;
import com.lotteryapp.lottery.dto.source.request.UpdateSourceRequest;
import com.lotteryapp.lottery.dto.source.response.SourceResponse;
import com.lotteryapp.lottery.repository.SourceRepository;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.*;

@Service
public class SourceService {

    private final SourceRepository sourceRepository;

    public SourceService(SourceRepository sourceRepository) {
        this.sourceRepository = sourceRepository;
    }

    public PageResponse<SourceResponse> list(ListSourcesRequest request) {
        if (request == null) request = new ListSourcesRequest();

        int pageNumber = (request.getPageNumber() == null) ? 0 : request.getPageNumber();
        int pageSize = (request.getPageSize() == null) ? 25 : request.getPageSize();

        if (pageNumber < 0) {
            throw new BadRequestException("pageNumber must be >= 0");
        }
        if (pageSize < 1 || pageSize > 200) {
            throw new BadRequestException("pageSize must be between 1 and 200");
        }

        Sort sort = parseSortOrDefault(request.getSort());
        Pageable pageable = PageRequest.of(pageNumber, pageSize, sort);

        String stateCode = normalizeState(request.getStateCode());
        Long gameModeId = request.getGameModeId();
        Boolean enabled = request.getEnabled();

        Page<Source> page = findPage(stateCode, gameModeId, enabled, pageable);

        List<SourceResponse> items = page.getContent().stream()
                .map(this::toResponse)
                .toList();

        PageResponse.PageMeta meta = PageResponse.PageMeta.builder()
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .hasNext(page.hasNext())
                .hasPrevious(page.hasPrevious())
                .sort(sortToString(sort))
                .build();

        return PageResponse.<SourceResponse>builder()
                .message("Sources loaded")
                .items(items)
                .meta(meta)
                .build();
    }

    @Transactional
    public ApiResponse<SourceResponse> create(CreateSourceRequest request) {
        if (request == null) {
            throw new BadRequestException("Request body is required");
        }

        validateCreate(request);

        Source source = Source.builder()
                .stateCode(normalizeState(request.getStateCode()))
                .gameModeId(request.getGameModeId())
                .priority(request.getPriority())
                .enabled(bool(request.getEnabled()))
                .sourceType(request.getSourceType())
                .parserKey(trimToNull(request.getParserKey()))
                .urlTemplate(trimToNull(request.getUrlTemplate()))
                .supportsGameList(bool(request.getSupportsGameList()))
                .drawLatest(bool(request.getDrawLatest()))
                .drawByDate(bool(request.getDrawByDate()))
                .drawHistory(bool(request.getDrawHistory()))
                .supportsRules(bool(request.getSupportsRules()))
                .supportsSchedule(bool(request.getSupportsSchedule()))
                .supportsJackpotAmount(bool(request.getSupportsJackpotAmount()))
                .supportsCashValue(bool(request.getSupportsCashValue()))
                .supportsDrawTime(bool(request.getSupportsDrawTime()))
                .supportsTimeZone(bool(request.getSupportsTimeZone()))
                .build();

        Source saved = sourceRepository.save(source);

        return ApiResponse.ok("Source created", toResponse(saved));
    }

    @Transactional
    public ApiResponse<SourceResponse> update(Long sourceId, UpdateSourceRequest request) {
        if (sourceId == null) {
            throw new BadRequestException("sourceId is required");
        }
        if (request == null) {
            throw new BadRequestException("Request body is required");
        }

        Source source = sourceRepository.findById(sourceId)
                .orElseThrow(() -> new NotFoundException("Source not found"));

        // Patch fields (only apply non-null)
        if (trimToNull(request.getStateCode()) != null) {
            source.setStateCode(normalizeState(request.getStateCode()));
        }
        if (request.getGameModeId() != null) {
            source.setGameModeId(request.getGameModeId());
        }
        if (request.getPriority() != null) {
            if (request.getPriority() < 0) throw new BadRequestException("priority must be >= 0");
            source.setPriority(request.getPriority());
        }
        if (request.getEnabled() != null) {
            source.setEnabled(request.getEnabled());
        }
        if (request.getSourceType() != null) {
            source.setSourceType(request.getSourceType());
        }
        if (trimToNull(request.getParserKey()) != null) {
            source.setParserKey(trimToNull(request.getParserKey()));
        }
        if (trimToNull(request.getUrlTemplate()) != null) {
            validateUrlTemplate(request.getUrlTemplate());
            source.setUrlTemplate(trimToNull(request.getUrlTemplate()));
        }

        if (request.getSupportsGameList() != null) source.setSupportsGameList(request.getSupportsGameList());
        if (request.getDrawLatest() != null) source.setDrawLatest(request.getDrawLatest());
        if (request.getDrawByDate() != null) source.setDrawByDate(request.getDrawByDate());
        if (request.getDrawHistory() != null) source.setDrawHistory(request.getDrawHistory());
        if (request.getSupportsRules() != null) source.setSupportsRules(request.getSupportsRules());
        if (request.getSupportsSchedule() != null) source.setSupportsSchedule(request.getSupportsSchedule());
        if (request.getSupportsJackpotAmount() != null) source.setSupportsJackpotAmount(request.getSupportsJackpotAmount());
        if (request.getSupportsCashValue() != null) source.setSupportsCashValue(request.getSupportsCashValue());
        if (request.getSupportsDrawTime() != null) source.setSupportsDrawTime(request.getSupportsDrawTime());
        if (request.getSupportsTimeZone() != null) source.setSupportsTimeZone(request.getSupportsTimeZone());

        Source saved = sourceRepository.save(source);

        return ApiResponse.ok("Source updated", toResponse(saved));
    }

    @Transactional
    public ApiResponse<Void> delete(DeleteSourceRequest request) {
        if (request == null || request.getSourceId() == null) {
            throw new BadRequestException("sourceId is required");
        }

        Long id = request.getSourceId();
        if (!sourceRepository.existsById(id)) {
            throw new NotFoundException("Source not found");
        }

        sourceRepository.deleteById(id);
        return ApiResponse.ok("Source deleted", null);
    }


    private Page<Source> findPage(String stateCode, Long gameModeId, Boolean enabled, Pageable pageable) {
        if (stateCode != null && gameModeId != null && enabled != null) {
            return sourceRepository.findByStateCodeIgnoreCaseAndGameModeIdAndEnabled(stateCode, gameModeId, enabled, pageable);
        }
        if (stateCode != null && gameModeId != null) {
            return sourceRepository.findByStateCodeIgnoreCaseAndGameModeId(stateCode, gameModeId, pageable);
        }
        if (stateCode != null && enabled != null) {
            return sourceRepository.findByStateCodeIgnoreCaseAndEnabled(stateCode, enabled, pageable);
        }
        if (gameModeId != null && enabled != null) {
            return sourceRepository.findByGameModeIdAndEnabled(gameModeId, enabled, pageable);
        }
        if (stateCode != null) {
            return sourceRepository.findByStateCodeIgnoreCase(stateCode, pageable);
        }
        if (gameModeId != null) {
            return sourceRepository.findByGameModeId(gameModeId, pageable);
        }
        if (enabled != null) {
            return sourceRepository.findByEnabled(enabled, pageable);
        }
        return sourceRepository.findAll(pageable);
    }


    private SourceResponse toResponse(Source s) {
        return SourceResponse.builder()
                .id(s.getId())
                .stateCode(s.getStateCode())
                .gameModeId(s.getGameModeId())
                .priority(s.getPriority())
                .enabled(s.isEnabled())
                .sourceType(s.getSourceType())
                .parserKey(s.getParserKey())
                .urlTemplate(s.getUrlTemplate())
                .supportsGameList(s.isSupportsGameList())
                .drawLatest(s.isDrawLatest())
                .drawByDate(s.isDrawByDate())
                .drawHistory(s.isDrawHistory())
                .supportsRules(s.isSupportsRules())
                .supportsSchedule(s.isSupportsSchedule())
                .supportsJackpotAmount(s.isSupportsJackpotAmount())
                .supportsCashValue(s.isSupportsCashValue())
                .supportsDrawTime(s.isSupportsDrawTime())
                .supportsTimeZone(s.isSupportsTimeZone())
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .build();
    }


    private void validateCreate(CreateSourceRequest r) {
        requireText(r.getStateCode(), "stateCode");
        if (r.getGameModeId() == null) throw new BadRequestException("gameModeId is required");
        if (r.getPriority() < 0) throw new BadRequestException("priority must be >= 0");
        if (r.getEnabled() == null) throw new BadRequestException("enabled is required");
        if (r.getSourceType() == null) throw new BadRequestException("sourceType is required");
        requireText(r.getParserKey(), "parserKey");
        requireText(r.getUrlTemplate(), "urlTemplate");
        validateUrlTemplate(r.getUrlTemplate());

        // capabilities are @NotNull already, but keep clean runtime errors:
        requireNotNull(r.getSupportsGameList(), "supportsGameList");
        requireNotNull(r.getDrawLatest(), "drawLatest");
        requireNotNull(r.getDrawByDate(), "drawByDate");
        requireNotNull(r.getDrawHistory(), "drawHistory");
        requireNotNull(r.getSupportsRules(), "supportsRules");
        requireNotNull(r.getSupportsSchedule(), "supportsSchedule");
        requireNotNull(r.getSupportsJackpotAmount(), "supportsJackpotAmount");
        requireNotNull(r.getSupportsCashValue(), "supportsCashValue");
        requireNotNull(r.getSupportsDrawTime(), "supportsDrawTime");
        requireNotNull(r.getSupportsTimeZone(), "supportsTimeZone");

    }

    private static void validateUrlTemplate(String urlTemplate) {
        String t = trimToNull(urlTemplate);
        if (t == null) throw new BadRequestException("urlTemplate is required");

        URI uri;
        try {
            uri = URI.create(t);
        } catch (Exception e) {
            throw new BadRequestException("urlTemplate is not a valid URI");
        }

        if (uri.getScheme() == null || !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new BadRequestException("urlTemplate must be https");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new BadRequestException("urlTemplate must include a host");
        }
    }

    private static Sort parseSortOrDefault(String sort) {
        String s = trimToNull(sort);
        if (s == null) {
            return Sort.by(Sort.Order.asc("priority"));
        }

        // Format: "field,asc" or "field,desc;field2,asc"
        List<Sort.Order> orders = new ArrayList<>();
        String[] parts = s.split(";");
        for (String part : parts) {
            String p = part.trim();
            if (p.isEmpty()) continue;

            String[] pair = p.split(",");
            String field = pair[0].trim();
            if (field.isEmpty()) continue;

            String dir = (pair.length >= 2) ? pair[1].trim().toLowerCase(Locale.ROOT) : "asc";
            Sort.Direction direction = "desc".equals(dir) ? Sort.Direction.DESC : Sort.Direction.ASC;

            orders.add(new Sort.Order(direction, field));
        }

        if (orders.isEmpty()) {
            return Sort.by(Sort.Order.asc("priority"));
        }
        return Sort.by(orders);
    }

    private static String sortToString(Sort sort) {
        if (sort == null || sort.isUnsorted()) return null;

        StringBuilder sb = new StringBuilder();
        for (Sort.Order o : sort) {
            if (sb.length() > 0) sb.append(";");
            sb.append(o.getProperty())
                    .append(",")
                    .append(o.getDirection().name().toLowerCase(Locale.ROOT));
        }
        return sb.toString();
    }

    private static boolean bool(Boolean v) {
        return v != null && v;
    }

    private static String normalizeState(String stateCode) {
        String s = trimToNull(stateCode);
        return (s == null) ? null : s.toUpperCase(Locale.ROOT);
    }

    private static void requireText(String value, String field) {
        if (trimToNull(value) == null) throw new BadRequestException(field + " is required");
    }

    private static void requireNotNull(Object value, String field) {
        if (value == null) throw new BadRequestException(field + " is required");
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
