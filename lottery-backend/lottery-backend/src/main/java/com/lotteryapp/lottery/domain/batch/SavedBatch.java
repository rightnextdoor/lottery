package com.lotteryapp.lottery.domain.batch;

import com.lotteryapp.lottery.domain.gamemode.GameMode;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;


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

    @Column(name = "keep_forever", nullable = false)
    @Builder.Default
    private Boolean keepForever = false;

    @Column(name = "expires_at")
    private Instant expiresAt;


    @OneToMany(mappedBy = "savedBatch", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ticketIndex ASC")
    @Builder.Default
    private List<Ticket> tickets = new ArrayList<>();

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();

        if (createdAt == null) createdAt = now;
        if (keepForever == null) keepForever = false;

        if (Boolean.TRUE.equals(keepForever)) {
            // Keep forever => no expiry
            expiresAt = null;
            return;
        }

        if (expiresAt == null) {
            expiresAt = OffsetDateTime.now(ZoneOffset.UTC)
                    .plusMonths(12)
                    .toInstant();
        }
    }

}
