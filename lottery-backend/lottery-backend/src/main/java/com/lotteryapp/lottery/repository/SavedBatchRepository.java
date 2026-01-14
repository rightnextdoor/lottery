package com.lotteryapp.lottery.repository;

import com.lotteryapp.lottery.domain.batch.SavedBatch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavedBatchRepository extends JpaRepository<SavedBatch, Long> {

    Page<SavedBatch> findByGameMode_Id(Long gameModeId, Pageable pageable);
}
