package com.lotteryapp.lottery.domain.batch;

import com.lotteryapp.lottery.domain.group.TicketGroup;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "ticket",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_ticket_batch_spec_ticketnum",
                        columnNames = {"saved_batch_id", "spec_number", "ticket_number"}
                )
        },
        indexes = {
                @Index(name = "ix_ticket_batch", columnList = "saved_batch_id"),
                @Index(name = "ix_ticket_batch_spec", columnList = "saved_batch_id,spec_number"),
                @Index(name = "ix_ticket_white_group", columnList = "white_group_id"),
                @Index(name = "ix_ticket_red_group", columnList = "red_group_id")
        }
)
@Getter
@Setter
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

    @Column(name = "spec_number", nullable = false)
    private Integer specNumber;

    @Column(name = "ticket_number", nullable = false)
    private Integer ticketNumber;

    @Column(name = "exclude_last_draw_numbers", nullable = false)
    @Builder.Default
    private Boolean excludeLastDrawNumbers = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "white_group_id")
    private TicketGroup whiteGroup;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "red_group_id")
    private TicketGroup redGroup;

    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("poolType ASC, position ASC")
    @Builder.Default
    private List<TicketPick> picks = new ArrayList<>();
}
