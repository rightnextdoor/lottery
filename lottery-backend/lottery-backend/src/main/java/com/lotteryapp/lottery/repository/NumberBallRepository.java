package com.lotteryapp.lottery.repository;

import com.lotteryapp.lottery.domain.numbers.NumberBall;
import com.lotteryapp.lottery.domain.numbers.PoolType;
import com.lotteryapp.lottery.domain.numbers.Tier;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NumberBallRepository extends JpaRepository<NumberBall, Long> {

    List<NumberBall> findByGameModeId(Long gameModeId);

    List<NumberBall> findByGameModeIdAndPoolType(Long gameModeId, PoolType poolType, Sort sort);

    Optional<NumberBall> findByGameModeIdAndPoolTypeAndNumberValue(
            Long gameModeId,
            PoolType poolType,
            Integer numberValue
    );

    List<NumberBall> findByGameModeIdAndPoolTypeAndTier(
            Long gameModeId,
            PoolType poolType,
            Tier tier,
            Sort sort
    );

    void deleteByGameModeId(Long gameModeId);
}
