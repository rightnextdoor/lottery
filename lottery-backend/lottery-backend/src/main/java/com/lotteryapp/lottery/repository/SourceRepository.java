package com.lotteryapp.lottery.repository;

import com.lotteryapp.lottery.domain.source.Source;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SourceRepository extends JpaRepository<Source, Long> {

    // existing fallback query you already have (keep it)
    List<Source> findByStateCodeInAndGameModeIdAndEnabledOrderByPriorityAsc(
            List<String> stateCodes,
            Long gameModeId,
            boolean enabled
    );

    // add these to match SourceService calls
    Page<Source> findByStateCodeIgnoreCaseAndGameModeIdAndEnabled(String stateCode, Long gameModeId, Boolean enabled, Pageable pageable);
    Page<Source> findByStateCodeIgnoreCaseAndGameModeId(String stateCode, Long gameModeId, Pageable pageable);
    Page<Source> findByStateCodeIgnoreCaseAndEnabled(String stateCode, Boolean enabled, Pageable pageable);
    Page<Source> findByGameModeIdAndEnabled(Long gameModeId, Boolean enabled, Pageable pageable);
    Page<Source> findByStateCodeIgnoreCase(String stateCode, Pageable pageable);
    Page<Source> findByGameModeId(Long gameModeId, Pageable pageable);
    Page<Source> findByEnabled(Boolean enabled, Pageable pageable);
}
