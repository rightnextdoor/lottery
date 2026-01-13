package com.lotteryapp.lottery.repository;

import com.lotteryapp.lottery.domain.group.GroupMode;
import com.lotteryapp.lottery.domain.group.TicketGroup;
import com.lotteryapp.lottery.domain.numbers.PoolType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketGroupRepository extends JpaRepository<TicketGroup, Long> {

    List<TicketGroup> findAllByGameMode_IdOrderByIdAsc(Long gameModeId);

    boolean existsByGameMode_IdAndPoolTypeAndGroupModeAndHotCountAndMidCountAndColdCount(
            Long gameModeId,
            PoolType poolType,
            GroupMode groupMode,
            Integer hotCount,
            Integer midCount,
            Integer coldCount
    );

    boolean existsByGameMode_IdAndPoolTypeAndGroupModeAndHotCountAndMidCountAndColdCountAndIdNot(
            Long gameModeId,
            PoolType poolType,
            GroupMode groupMode,
            Integer hotCount,
            Integer midCount,
            Integer coldCount,
            Long id
    );

    boolean existsByGameMode_IdAndPoolTypeAndGroupModeAndHotPctAndMidPctAndColdPct(
            Long gameModeId,
            PoolType poolType,
            GroupMode groupMode,
            Integer hotPct,
            Integer midPct,
            Integer coldPct
    );

    boolean existsByGameMode_IdAndPoolTypeAndGroupModeAndHotPctAndMidPctAndColdPctAndIdNot(
            Long gameModeId,
            PoolType poolType,
            GroupMode groupMode,
            Integer hotPct,
            Integer midPct,
            Integer coldPct,
            Long id
    );
}