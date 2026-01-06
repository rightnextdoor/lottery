package com.lotteryapp.lottery.domain.batch;

import com.lotteryapp.lottery.domain.numbers.PoolType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "ticket_pick",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_pick_ticket_pool_position",
                        columnNames = {"ticket_id", "pool_type", "position"}
                )
        },
        indexes = {
                @Index(name = "ix_pick_ticket", columnList = "ticket_id")
        }
)
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketPick {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @Enumerated(EnumType.STRING)
    @Column(name = "pool_type", nullable = false, length = 10)
    private PoolType poolType;

    @Column(nullable = false)
    private Integer position;

    @Column(name = "number_value", nullable = false)
    private Integer numberValue;
}
