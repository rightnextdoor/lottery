package com.lotteryapp.lottery.service;

import com.lotteryapp.common.exception.BadRequestException;
import com.lotteryapp.common.exception.NotFoundException;
import com.lotteryapp.lottery.domain.batch.generator.*;
import com.lotteryapp.lottery.domain.gamemode.GameMode;
import com.lotteryapp.lottery.domain.gamemode.Rules;
import com.lotteryapp.lottery.domain.group.TicketGroup;
import com.lotteryapp.lottery.domain.numbers.NumberBall;
import com.lotteryapp.lottery.domain.numbers.PoolType;
import com.lotteryapp.lottery.dto.ticketgen.request.GenerateBatchRequest;
import com.lotteryapp.lottery.dto.ticketgen.request.TicketSpecRequest;
import com.lotteryapp.lottery.dto.ticketgen.response.GeneratedBatchResponse;
import com.lotteryapp.lottery.dto.ticketgen.response.GeneratedPicksResponse;
import com.lotteryapp.lottery.dto.ticketgen.response.GeneratedSpecResultResponse;
import com.lotteryapp.lottery.dto.ticketgen.response.GeneratedTicketResponse;
import com.lotteryapp.lottery.repository.GameModeRepository;
import com.lotteryapp.lottery.repository.NumberBallRepository;
import com.lotteryapp.lottery.repository.RulesRepository;
import com.lotteryapp.lottery.repository.TicketGroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class TicketGeneratorService {

    private final GameModeRepository gameModeRepository;
    private final TicketGroupRepository ticketGroupRepository;
    private final NumberBallRepository numberBallRepository;

    private final TicketGeneratorEngine engine = new TicketGeneratorEngine();

    @Transactional(readOnly = true)
    public GeneratedBatchResponse generate(GenerateBatchRequest request) {
        if (request == null) throw new BadRequestException("Request is required.");

        Long gameModeId = request.gameModeId();
        if (gameModeId == null) throw new BadRequestException("gameModeId is required.");
        if (request.ticketSpecs() == null || request.ticketSpecs().isEmpty()) {
            throw new BadRequestException("ticketSpecs must contain at least one spec.");
        }

        GameMode gameMode = gameModeRepository.findById(gameModeId)
                .orElseThrow(() -> new NotFoundException("GameMode not found: " + gameModeId));

        Rules rules = gameMode.getRules();
        if (rules == null) {
            throw new BadRequestException("Rules are not set for gameModeId: " + gameModeId);
        }

        Sort byNumberAsc = Sort.by(Sort.Direction.ASC, "numberValue");

        List<NumberBall> whiteBalls = numberBallRepository.findByGameModeIdAndPoolType(gameModeId, PoolType.WHITE, byNumberAsc);
        List<NumberBall> redBalls = numberBallRepository.findByGameModeIdAndPoolType(gameModeId, PoolType.RED, byNumberAsc);

        Set<Integer> latestWhite = parseCsvInts(gameMode.getLatestWhiteWinningCsv());
        Set<Integer> latestRed = parseCsvInts(gameMode.getLatestRedWinningCsv());

        GeneratorContext ctx = new GeneratorContext(rules, whiteBalls, redBalls, GeneratorOptions.defaults());

        List<GeneratorSpec> specs = new ArrayList<>();

        for (TicketSpecRequest specReq : request.ticketSpecs()) {
            if (specReq.ticketCount() <= 0) {
                throw new BadRequestException("ticketCount must be > 0 for every spec.");
            }

            TicketGroup whiteGroup = resolveGroupOrNull(specReq.whiteGroupId(), gameModeId, PoolType.WHITE);
            TicketGroup redGroup = resolveGroupOrNull(specReq.redGroupId(), gameModeId, PoolType.RED);

            boolean excludeLastDrawNumbers = specReq.excludeLastDrawNumbers();

            specs.add(new GeneratorSpec(
                    specReq.ticketCount(),
                    whiteGroup,
                    redGroup,
                    excludeLastDrawNumbers,
                    excludeLastDrawNumbers ? latestWhite : Set.of(),
                    excludeLastDrawNumbers ? latestRed : Set.of()
            ));
        }

        GeneratedBatch generated = engine.generate(ctx, specs);
        return mapToResponse(generated);
    }

    private TicketGroup resolveGroupOrNull(Long groupId, Long gameModeId, PoolType expectedPool) {
        if (groupId == null) return null;

        TicketGroup group = ticketGroupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException("TicketGroup not found: " + groupId));

        if (group.getGameMode() == null || group.getGameMode().getId() == null) {
            throw new BadRequestException("TicketGroup is missing gameMode link: " + groupId);
        }
        if (!Objects.equals(group.getGameMode().getId(), gameModeId)) {
            throw new BadRequestException("TicketGroup " + groupId + " does not belong to gameModeId " + gameModeId + ".");
        }
        if (group.getPoolType() != expectedPool) {
            throw new BadRequestException("TicketGroup " + groupId + " is for " + group.getPoolType() + " but expected " + expectedPool + ".");
        }

        return group;
    }

    private Set<Integer> parseCsvInts(String csv) {
        if (csv == null || csv.isBlank()) return Set.of();
        String[] parts = csv.split(",");
        Set<Integer> out = new LinkedHashSet<>();
        for (String p : parts) {
            String s = p == null ? "" : p.trim();
            if (s.isEmpty()) continue;
            try {
                out.add(Integer.parseInt(s));
            } catch (NumberFormatException ignored) {
                // ignore bad token
            }
        }
        return out;
    }

    private GeneratedBatchResponse mapToResponse(GeneratedBatch batch) {
        List<GeneratedSpecResultResponse> specResults = batch.getSpecResults().stream()
                .map(spec -> {
                    List<GeneratedTicketResponse> tickets = spec.getTickets().stream()
                            .map(t -> new GeneratedTicketResponse(
                                    t.ticketNumber(),
                                    new GeneratedPicksResponse(t.picks().white(), t.picks().red())
                            ))
                            .toList();

                    return new GeneratedSpecResultResponse(
                            spec.getSpecNumber(),
                            spec.getTicketCount(),
                            spec.getWhiteGroupId(),
                            spec.getRedGroupId(),
                            spec.isExcludeLastDrawNumbers(),
                            tickets,
                            spec.getWarnings()
                    );
                })
                .toList();

        return new GeneratedBatchResponse(specResults, batch.getWarnings());
    }
}
