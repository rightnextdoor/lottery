package com.lotteryapp.lottery.domain.batch;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "ticket",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_ticket_batch_index", columnNames = {"saved_batch_id", "ticket_index"})
        }
)
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "saved_batch_id", nullable = false)
    private SavedBatch savedBatch;


    @Column(name = "ticket_index", nullable = false)
    private Integer ticketIndex;

    @Column(name = "strategy_group", length = 20)
    private String strategyGroup;

    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("poolType ASC, position ASC")
    @Builder.Default
    private List<TicketPick> picks = new ArrayList<>();
}
