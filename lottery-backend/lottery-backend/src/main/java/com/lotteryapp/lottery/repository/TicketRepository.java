package com.lotteryapp.lottery.repository;

import com.lotteryapp.lottery.domain.batch.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    List<Ticket> findBySavedBatch_IdOrderBySpecNumberAscTicketNumberAsc(Long savedBatchId);
}
