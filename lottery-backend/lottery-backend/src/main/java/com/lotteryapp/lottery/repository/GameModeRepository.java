package com.lotteryapp.lottery.repository;

import com.lotteryapp.lottery.domain.gamemode.GameMode;
import com.lotteryapp.lottery.domain.gamemode.GameScope;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GameModeRepository extends JpaRepository<GameMode, Long> {

    boolean existsByModeKeyIgnoreCase(String modeKey);

    @Query("""
        select gm
        from GameMode gm
        where gm.scope = com.lotteryapp.lottery.domain.gamemode.GameScope.MULTI_STATE
           or (gm.scope = com.lotteryapp.lottery.domain.gamemode.GameScope.STATE_ONLY
               and upper(gm.jurisdiction.code) = upper(:stateCode))
        order by gm.displayName asc
    """)
    List<GameMode> findAvailableForState(@Param("stateCode") String stateCode);

    @Query("""
        select gm
        from GameMode gm
        where (:scope is null or gm.scope = :scope)
          and (
               :stateCode is null
               or gm.scope = com.lotteryapp.lottery.domain.gamemode.GameScope.MULTI_STATE
               or (gm.scope = com.lotteryapp.lottery.domain.gamemode.GameScope.STATE_ONLY
                   and upper(gm.jurisdiction.code) = upper(:stateCode))
          )
          and (
               :q is null
               or lower(gm.modeKey) like lower(concat('%', :q, '%'))
               or lower(gm.displayName) like lower(concat('%', :q, '%'))
          )
        order by gm.displayName asc
    """)
    Page<GameMode> search(
            @Param("q") String q,
            @Param("scope") GameScope scope,
            @Param("stateCode") String stateCode,
            Pageable pageable
    );
}
