package com.lotteryapp.lottery.repository;

import com.lotteryapp.lottery.domain.batch.BatchCheckRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BatchCheckRecordRepository extends JpaRepository<BatchCheckRecord, Long> {

    List<BatchCheckRecord> findBySavedBatch_Id(Long savedBatchId);

    List<BatchCheckRecord> findBySavedBatch_IdAndDrawDate(Long savedBatchId, LocalDate drawDate);

    Optional<BatchCheckRecord> findBySavedBatch_IdAndDrawDateAndSpecNumber(
            Long savedBatchId,
            LocalDate drawDate,
            Integer specNumber
    );

    void deleteBySavedBatch_Id(Long savedBatchId);
}
