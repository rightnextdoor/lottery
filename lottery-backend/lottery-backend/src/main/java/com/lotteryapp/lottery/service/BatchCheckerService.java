package com.lotteryapp.lottery.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lotteryapp.common.exception.BadRequestException;
import com.lotteryapp.common.exception.NotFoundException;
import com.lotteryapp.lottery.domain.batch.BatchCheckRecord;
import com.lotteryapp.lottery.domain.batch.SavedBatch;
import com.lotteryapp.lottery.domain.batch.Ticket;
import com.lotteryapp.lottery.domain.batch.TicketPick;
import com.lotteryapp.lottery.domain.gamemode.GameMode;
import com.lotteryapp.lottery.domain.gamemode.Rules;
import com.lotteryapp.lottery.domain.numbers.PoolType;
import com.lotteryapp.lottery.dto.batch.request.BatchCheckRequest;
import com.lotteryapp.lottery.dto.batch.response.BatchCheckRecordResponse;
import com.lotteryapp.lottery.dto.batch.response.BatchCheckResponse;
import com.lotteryapp.lottery.dto.draw.response.DrawResponse;
import com.lotteryapp.lottery.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BatchCheckerService {

    private final SavedBatchRepository savedBatchRepository;
    private final TicketRepository ticketRepository;
    private final BatchCheckRecordRepository recordRepository;
    private final DrawService drawService;
    private final GameModeRepository gameModeRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public BatchCheckResponse checkBatch(BatchCheckRequest request) {
        if (request == null) throw new BadRequestException("Request is required.");
        if (request.batchId() == null) throw new BadRequestException("batchId is required.");

        SavedBatch batch = savedBatchRepository.findById(request.batchId())
                .orElseThrow(() -> new NotFoundException("SavedBatch not found: " + request.batchId()));

        Long gameModeId = (batch.getGameMode() == null) ? null : batch.getGameMode().getId();
        if (gameModeId == null) throw new BadRequestException("SavedBatch is missing gameMode link.");

        String stateCode = (batch.getGameMode() == null || batch.getGameMode().getJurisdiction() == null)
                ? null
                : batch.getGameMode().getJurisdiction().getCode();
        if (stateCode == null || stateCode.isBlank()) {
            throw new BadRequestException("GameMode jurisdiction code (stateCode) is required to check a batch.");
        }

        Rules rules = requireRules(gameModeId);

        Integer maxWhitePick = rules.getWhitePickCount();
        if (maxWhitePick == null || maxWhitePick < 1) {
            throw new BadRequestException("Rules.whitePickCount is required to check a batch.");
        }

        // Get winning numbers via the correct service
        DrawResponse draw = drawService.getWinningNumbersForCheck(gameModeId, stateCode, request.drawDate());
        LocalDate drawDate = draw.getDrawDate();

        List<Ticket> tickets = ticketRepository.findBySavedBatch_IdOrderBySpecNumberAscTicketNumberAsc(batch.getId());
        if (tickets.isEmpty()) throw new BadRequestException("Batch has no tickets.");

        // Group tickets by specNumber (spec-level records)
        Map<Integer, List<Ticket>> bySpec = tickets.stream()
                .collect(Collectors.groupingBy(Ticket::getSpecNumber, TreeMap::new, Collectors.toList()));

        Set<Integer> winningWhite = new HashSet<>(safe(draw.getWhiteNumbers()));
        Set<Integer> winningRed = new HashSet<>(safe(draw.getRedNumbers()));

        List<BatchCheckRecordResponse> specRecords = new ArrayList<>();

        for (Map.Entry<Integer, List<Ticket>> entry : bySpec.entrySet()) {
            Integer specNumber = entry.getKey();
            List<Ticket> specTickets = entry.getValue();

            SpecStats stats = computeSpecStats(specTickets, winningWhite, winningRed, maxWhitePick);

            BatchCheckRecord record = recordRepository
                    .findBySavedBatch_IdAndDrawDateAndSpecNumber(batch.getId(), drawDate, specNumber)
                    .orElseGet(() -> BatchCheckRecord.builder()
                            .savedBatch(batch)
                            .drawDate(drawDate)
                            .specNumber(specNumber)
                            .build());

            // store group ids used for this spec (all tickets in spec share the same groups)
            Ticket first = specTickets.get(0);
            record.setWhiteGroupId(first.getWhiteGroup() == null ? null : first.getWhiteGroup().getId());
            record.setRedGroupId(first.getRedGroup() == null ? null : first.getRedGroup().getId());

            record.setPctAnyHit(stats.pctAnyHit);
            record.setPctRedHit(stats.pctRedHit);
            record.setWhiteHitPctJson(writeJson(stats.whiteHitPct));

            BatchCheckRecord saved = recordRepository.save(record);

            specRecords.add(new BatchCheckRecordResponse(
                    saved.getId(),
                    saved.getDrawDate(),
                    saved.getSpecNumber(),
                    saved.getWhiteGroupId(),
                    saved.getRedGroupId(),
                    saved.getPctAnyHit(),
                    saved.getPctRedHit(),
                    stats.whiteHitPct
            ));
        }

        batch.setChecked(true);
        savedBatchRepository.save(batch);

        return new BatchCheckResponse(batch.getId(), drawDate, specRecords);
    }

    private GameMode requireGameMode(Long gameModeId) {
        return gameModeRepository.findById(gameModeId)
                .orElseThrow(() -> new NotFoundException("GameMode not found: " + gameModeId));
    }

    private Rules requireRules(Long gameModeId) {
        GameMode mode = requireGameMode(gameModeId);
        Rules rules = mode.getRules();
        if (rules == null) {
            throw new BadRequestException("Rules are not set for gameModeId: " + gameModeId);
        }
        return rules;
    }


    private SpecStats computeSpecStats(
            List<Ticket> tickets,
            Set<Integer> winningWhite,
            Set<Integer> winningRed,
            int maxWhitePick
    ) {
        int total = tickets.size();

        int anyHitTickets = 0;
        int redHitTickets = 0;

        Map<Integer, Integer> whiteHitCounts = new LinkedHashMap<>();
        for (int i = 1; i <= maxWhitePick; i++) whiteHitCounts.put(i, 0);

        for (Ticket t : tickets) {
            List<Integer> white = picksOf(t, PoolType.WHITE);
            List<Integer> red = picksOf(t, PoolType.RED);

            int whiteHits = 0;
            for (Integer n : white) {
                if (n != null && winningWhite.contains(n)) whiteHits++;
            }

            boolean redHit = false;
            for (Integer n : red) {
                if (n != null && winningRed.contains(n)) {
                    redHit = true;
                    break;
                }
            }

            if (whiteHits > 0 || redHit) anyHitTickets++;
            if (redHit) redHitTickets++;

            if (whiteHits >= 1 && whiteHits <= maxWhitePick) {
                whiteHitCounts.put(whiteHits, whiteHitCounts.get(whiteHits) + 1);
            }
        }

        Map<Integer, Double> whiteHitPct = new LinkedHashMap<>();
        for (int i = 1; i <= maxWhitePick; i++) {
            whiteHitPct.put(i, pct(whiteHitCounts.get(i), total));
        }

        return new SpecStats(
                pct(anyHitTickets, total),
                pct(redHitTickets, total),
                whiteHitPct
        );
    }

    private List<Integer> picksOf(Ticket t, PoolType poolType) {
        if (t.getPicks() == null) return List.of();

        return t.getPicks().stream()
                .filter(p -> p.getPoolType() == poolType)
                .sorted(Comparator.comparingInt(TicketPick::getPosition))
                .map(TicketPick::getNumberValue)
                .toList();
    }

    private double pct(Integer count, int total) {
        if (count == null || total <= 0) return 0.0;
        return (count * 100.0) / total;
    }

    private String writeJson(Map<Integer, Double> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return "{}";
        }
    }

    private <T> List<T> safe(List<T> in) {
        return in == null ? List.of() : in;
    }

    private static class SpecStats {
        final double pctAnyHit;
        final double pctRedHit;
        final Map<Integer, Double> whiteHitPct;

        SpecStats(double pctAnyHit, double pctRedHit, Map<Integer, Double> whiteHitPct) {
            this.pctAnyHit = pctAnyHit;
            this.pctRedHit = pctRedHit;
            this.whiteHitPct = whiteHitPct;
        }
    }
}
