package com.lotteryapp.lottery.repository;

import com.lotteryapp.lottery.domain.draw.DrawConflict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface DrawConflictRepository extends JpaRepository<DrawConflict, Long> {

    Optional<DrawConflict> findByDrawResultId(Long drawResultId);

    Optional<DrawConflict> findByGameModeIdAndDrawDate(Long gameModeId, LocalDate drawDate);

    long countByGameModeId(Long gameModeId);

    Page<DrawConflict> findByGameModeIdOrderByDrawDateDesc(Long gameModeId, Pageable pageable);
}
