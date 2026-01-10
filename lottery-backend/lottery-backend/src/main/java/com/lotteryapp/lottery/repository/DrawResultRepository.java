package com.lotteryapp.lottery.repository;

import com.lotteryapp.lottery.domain.draw.DrawResult;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DrawResultRepository extends JpaRepository<DrawResult, Long> {

    Optional<DrawResult> findByGameModeIdAndDrawDate(Long gameModeId, LocalDate drawDate);

    Optional<DrawResult> findTopByGameModeIdOrderByDrawDateDesc(Long gameModeId);

    List<DrawResult> findByGameModeIdOrderByDrawDateDesc(Long gameModeId, Pageable pageable);

    List<DrawResult> findByGameModeIdAndDrawDateBetweenOrderByDrawDateAsc(
            Long gameModeId,
            LocalDate start,
            LocalDate end
    );
}
