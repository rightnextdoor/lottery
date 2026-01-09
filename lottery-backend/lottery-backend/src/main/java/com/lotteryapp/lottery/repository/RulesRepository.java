package com.lotteryapp.lottery.repository;

import com.lotteryapp.lottery.domain.gamemode.Rules;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RulesRepository extends JpaRepository<Rules, Long> {
}
