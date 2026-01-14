package com.lotteryapp.lottery.repository;

import com.lotteryapp.lottery.domain.batch.generator.SavedGeneratorSpec;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SavedGeneratorSpecRepository extends JpaRepository<SavedGeneratorSpec, Long> {

    List<SavedGeneratorSpec> findByGameModeId(Long gameModeId);

    Optional<SavedGeneratorSpec> findByIdAndGameModeId(Long id, Long gameModeId);
}
