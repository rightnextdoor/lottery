package com.lotteryapp.lottery.repository;

import com.lotteryapp.lottery.domain.jurisdiction.Jurisdiction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JurisdictionRepository extends JpaRepository<Jurisdiction, String> {

    Optional<Jurisdiction> findByCodeIgnoreCase(String code);
}
