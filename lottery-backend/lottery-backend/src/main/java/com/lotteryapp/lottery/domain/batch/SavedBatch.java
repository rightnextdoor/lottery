package com.lotteryapp.lottery.domain.batch;

import com.lotteryapp.lottery.domain.gamemode.GameMode;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "saved_batch")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SavedBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "game_mode_id", nullable = false)
    private GameMode gameMode;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;


    @OneToMany(mappedBy = "savedBatch", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ticketIndex ASC")
    @Builder.Default
    private List<Ticket> tickets = new ArrayList<>();

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
