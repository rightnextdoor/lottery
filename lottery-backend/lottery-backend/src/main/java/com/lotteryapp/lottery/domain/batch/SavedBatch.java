package com.lotteryapp.lottery.domain.batch;

import com.lotteryapp.lottery.domain.gamemode.GameMode;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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

    @Column(name = "keep_forever", nullable = false)
    @Builder.Default
    private Boolean keepForever = false;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "checked", nullable = false)
    @Builder.Default
    private Boolean checked = false;

    @OneToMany(mappedBy = "savedBatch", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("specNumber ASC, ticketNumber ASC")
    @Builder.Default
    private List<Ticket> tickets = new ArrayList<>();

    @OneToMany(mappedBy = "savedBatch", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("drawDate DESC, specNumber ASC")
    @Builder.Default
    private List<BatchCheckRecord> checkRecords = new ArrayList<>();

    @Transient
    public BatchStatus getStatus() {
        if (Boolean.TRUE.equals(keepForever)) return BatchStatus.NONE;

        Instant exp = expiresAt;
        if (exp == null) return BatchStatus.NONE;

        boolean expired = exp.isBefore(Instant.now());
        boolean isChecked = Boolean.TRUE.equals(checked);

        if (expired && !isChecked) return BatchStatus.EXPIRED_NOT_CHECKED;
        return BatchStatus.NONE;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();

        if (createdAt == null) createdAt = now;
        if (keepForever == null) keepForever = false;
        if (checked == null) checked = false;

        if (Boolean.TRUE.equals(keepForever)) {
            expiresAt = null;
            return;
        }

        if (expiresAt == null) {
            // default auto-delete window: 12 months (but only eligible once checked=true in cleanup logic)
            expiresAt = OffsetDateTime.now(ZoneOffset.UTC)
                    .plusMonths(12)
                    .toInstant();
        }
    }
}
