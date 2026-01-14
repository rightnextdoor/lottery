package com.lotteryapp.lottery.service;

import com.lotteryapp.common.exception.BadRequestException;
import com.lotteryapp.common.exception.NotFoundException;
import com.lotteryapp.lottery.domain.batch.*;
import com.lotteryapp.lottery.domain.group.TicketGroup;
import com.lotteryapp.lottery.domain.gamemode.GameMode;
import com.lotteryapp.lottery.dto.batch.request.*;
import com.lotteryapp.lottery.dto.batch.response.*;
import com.lotteryapp.lottery.repository.GameModeRepository;
import com.lotteryapp.lottery.repository.SavedBatchRepository;
import com.lotteryapp.lottery.repository.TicketGroupRepository;
import com.lotteryapp.lottery.repository.TicketRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class BatchService {

    private static final ZoneId CHICAGO = ZoneId.of("America/Chicago");
    private static final DateTimeFormatter NAME_FMT = DateTimeFormatter.ofPattern("MM-dd-yyyy h:mm a");

    private final SavedBatchRepository savedBatchRepository;
    private final GameModeRepository gameModeRepository;
    private final TicketGroupRepository ticketGroupRepository;
    private final TicketRepository ticketRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final BatchCheckerService batchCheckerService;

    @Transactional
    public SavedBatchResponse saveBatch(SaveBatchRequest request) {
        if (request == null) throw new BadRequestException("Request is required.");
        if (request.gameModeId() == null) throw new BadRequestException("gameModeId is required.");
        if (request.specResults() == null || request.specResults().isEmpty()) {
            throw new BadRequestException("specResults is required.");
        }

        GameMode gameMode = gameModeRepository.findById(request.gameModeId())
                .orElseThrow(() -> new NotFoundException("GameMode not found: " + request.gameModeId()));

        Instant now = Instant.now();
        String name = buildBatchName(gameMode, now);

        SavedBatch batch = SavedBatch.builder()
                .gameMode(gameMode)
                .name(name)
                .createdAt(now)
                .keepForever(Boolean.TRUE.equals(request.keepForever()))
                .checked(false)
                .build();

        // expiresAt is managed by SavedBatch @PrePersist (and keepForever rule)
        // but we will enforce keepForever behavior here too:
        if (Boolean.TRUE.equals(batch.getKeepForever())) {
            batch.setExpiresAt(null);
        }

        // Build Tickets from specResults
        int specNumber = 1;
        for (SavedSpecResultRequest spec : request.specResults()) {
            TicketGroup whiteGroup = resolveGroupOrNull(spec.whiteGroupId(), request.gameModeId(), "WHITE");
            TicketGroup redGroup = resolveGroupOrNull(spec.redGroupId(), request.gameModeId(), "RED");

            if (spec.tickets() == null || spec.tickets().isEmpty()) {
                throw new BadRequestException("Spec " + specNumber + " must include tickets.");
            }

            for (SavedTicketRequest t : spec.tickets()) {
                Ticket ticket = Ticket.builder()
                        .savedBatch(batch)
                        .specNumber(specNumber)
                        .ticketNumber(t.ticketNumber())
                        .excludeLastDrawNumbers(spec.excludeLastDrawNumbers())
                        .whiteGroup(whiteGroup)
                        .redGroup(redGroup)
                        .build();

                // Picks
                List<TicketPick> picks = new ArrayList<>();

                int pos = 1;
                for (Integer n : safeList(t.picks().white())) {
                    picks.add(TicketPick.builder()
                            .ticket(ticket)
                            .poolType(com.lotteryapp.lottery.domain.numbers.PoolType.WHITE)
                            .position(pos++)
                            .numberValue(n)
                            .build());
                }

                pos = 1;
                for (Integer n : safeList(t.picks().red())) {
                    picks.add(TicketPick.builder()
                            .ticket(ticket)
                            .poolType(com.lotteryapp.lottery.domain.numbers.PoolType.RED)
                            .position(pos++)
                            .numberValue(n)
                            .build());
                }

                ticket.setPicks(picks);
                batch.getTickets().add(ticket);
            }

            specNumber++;
        }

        SavedBatch saved = savedBatchRepository.save(batch);
        return toSavedBatchResponse(saved, true);
    }

    @Transactional(readOnly = true)
    public Page<SavedBatchResponse> listBatches(ListBatchesRequest request) {
        if (request == null) throw new BadRequestException("Request is required.");
        if (request.gameModeId() == null) throw new BadRequestException("gameModeId is required.");

        int page = Math.max(0, request.page());
        int size = Math.max(1, request.size());

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<SavedBatch> batches = savedBatchRepository.findByGameMode_Id(request.gameModeId(), pageable);

        // list view: include checkRecords, omit tickets (heavy)
        return batches.map(b -> toSavedBatchResponse(b, false));
    }

    @Transactional(readOnly = true)
    public SavedBatchResponse getBatchDetail(GetBatchDetailRequest request) {
        if (request == null) throw new BadRequestException("Request is required.");
        if (request.batchId() == null) throw new BadRequestException("batchId is required.");

        SavedBatch batch = savedBatchRepository.findById(request.batchId())
                .orElseThrow(() -> new NotFoundException("SavedBatch not found: " + request.batchId()));

        // detail view: include tickets + records
        return toSavedBatchResponse(batch, true);
    }

    @Transactional
    public void deleteBatch(DeleteBatchRequest request) {
        if (request == null) throw new BadRequestException("Request is required.");
        if (request.batchId() == null) throw new BadRequestException("batchId is required.");

        SavedBatch batch = savedBatchRepository.findById(request.batchId())
                .orElseThrow(() -> new NotFoundException("SavedBatch not found: " + request.batchId()));

        savedBatchRepository.delete(batch);
    }

    @Transactional
    public SavedBatchResponse updateKeepForever(UpdateKeepForeverRequest request) {
        if (request == null) throw new BadRequestException("Request is required.");
        if (request.batchId() == null) throw new BadRequestException("batchId is required.");
        if (request.keepForever() == null) throw new BadRequestException("keepForever is required.");

        SavedBatch batch = savedBatchRepository.findById(request.batchId())
                .orElseThrow(() -> new NotFoundException("SavedBatch not found: " + request.batchId()));

        boolean keep = Boolean.TRUE.equals(request.keepForever());
        batch.setKeepForever(keep);

        if (keep) {
            batch.setExpiresAt(null);
        } else {
            Instant created = batch.getCreatedAt() == null ? Instant.now() : batch.getCreatedAt();
            batch.setExpiresAt(OffsetDateTime.ofInstant(created, ZoneOffset.UTC).plusMonths(12).toInstant());
        }

        SavedBatch saved = savedBatchRepository.save(batch);
        return toSavedBatchResponse(saved, false);
    }

    @Transactional
    public BatchCheckResponse checkBatch(BatchCheckRequest request) {
        return batchCheckerService.checkBatch(request);
    }

    private String buildBatchName(GameMode gameMode, Instant createdAt) {
        String gm = (gameMode.getDisplayName() == null || gameMode.getDisplayName().isBlank())
                ? "Batch"
                : gameMode.getDisplayName().trim();

        String ts = LocalDateTime.ofInstant(createdAt, CHICAGO).format(NAME_FMT);
        return gm + " " + ts;
    }

    private TicketGroup resolveGroupOrNull(Long groupId, Long gameModeId, String expectedPool) {
        if (groupId == null) return null;

        TicketGroup group = ticketGroupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException("TicketGroup not found: " + groupId));

        if (group.getGameMode() == null || group.getGameMode().getId() == null) {
            throw new BadRequestException("TicketGroup is missing gameMode link: " + groupId);
        }
        if (!Objects.equals(group.getGameMode().getId(), gameModeId)) {
            throw new BadRequestException("TicketGroup " + groupId + " does not belong to gameModeId " + gameModeId + ".");
        }
        if (group.getPoolType() == null || !group.getPoolType().name().equals(expectedPool)) {
            throw new BadRequestException("TicketGroup " + groupId + " poolType is not " + expectedPool + ".");
        }

        return group;
    }

    private <T> List<T> safeList(List<T> in) {
        return in == null ? List.of() : in;
    }

    private SavedBatchResponse toSavedBatchResponse(SavedBatch batch, boolean includeTickets) {
        List<BatchCheckRecordResponse> records = new ArrayList<>();
        if (batch.getCheckRecords() != null) {
            for (BatchCheckRecord r : batch.getCheckRecords()) {
                records.add(toRecordResponse(r));
            }
        }

        List<TicketResponse> tickets = null;
        if (includeTickets) {
            tickets = new ArrayList<>();
            List<Ticket> loaded = (batch.getTickets() != null) ? batch.getTickets()
                    : ticketRepository.findBySavedBatch_IdOrderBySpecNumberAscTicketNumberAsc(batch.getId());

            for (Ticket t : loaded) {
                tickets.add(toTicketResponse(t));
            }
        }

        return new SavedBatchResponse(
                batch.getId(),
                batch.getGameMode() == null ? null : batch.getGameMode().getId(),
                batch.getName(),
                batch.getCreatedAt(),
                batch.getKeepForever(),
                batch.getExpiresAt(),
                batch.getChecked(),
                batch.getStatus() == null ? null : batch.getStatus().name(),
                tickets,
                records
        );
    }

    private TicketResponse toTicketResponse(Ticket t) {
        List<TicketPickResponse> picks = new ArrayList<>();
        if (t.getPicks() != null) {
            for (TicketPick p : t.getPicks()) {
                picks.add(new TicketPickResponse(
                        p.getPoolType() == null ? null : p.getPoolType().name(),
                        p.getPosition(),
                        p.getNumberValue()
                ));
            }
        }

        return new TicketResponse(
                t.getId(),
                t.getSpecNumber(),
                t.getTicketNumber(),
                t.getExcludeLastDrawNumbers(),
                t.getWhiteGroup() == null ? null : t.getWhiteGroup().getId(),
                t.getRedGroup() == null ? null : t.getRedGroup().getId(),
                picks
        );
    }

    private BatchCheckRecordResponse toRecordResponse(BatchCheckRecord r) {
        Map<Integer, Double> whiteHitPct = new LinkedHashMap<>();
        try {
            if (r.getWhiteHitPctJson() != null && !r.getWhiteHitPctJson().isBlank()) {
                whiteHitPct = objectMapper.readValue(r.getWhiteHitPctJson(), new TypeReference<Map<Integer, Double>>() {});
            }
        } catch (Exception ignored) {
            // if JSON parsing fails, return empty map
        }

        return new BatchCheckRecordResponse(
                r.getId(),
                r.getDrawDate(),
                r.getSpecNumber(),
                r.getWhiteGroupId(),
                r.getRedGroupId(),
                r.getPctAnyHit(),
                r.getPctRedHit(),
                whiteHitPct
        );
    }
}
