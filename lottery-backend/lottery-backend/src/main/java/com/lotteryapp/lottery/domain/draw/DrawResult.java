package com.lotteryapp.lottery.domain.draw;

import com.lotteryapp.lottery.domain.gamemode.GameMode;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;


@Entity
@Table(
        name = "draw_result",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_draw_game_date",
                        columnNames = {"game_mode_id", "draw_date"}
                )
        },
        indexes = {
                @Index(name = "ix_draw_game_mode", columnList = "game_mode_id"),
                @Index(name = "ix_draw_date", columnList = "draw_date")
        }
)
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DrawResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "game_mode_id", nullable = false)
    private GameMode gameMode;

    @Column(name = "draw_date", nullable = false)
    private LocalDate drawDate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "cached_until", nullable = false)
    private Instant cachedUntil;


    @Column(name = "source_name", length = 80)
    private String sourceName;

    @Column(name = "source_ref", length = 300)
    private String sourceRef;

    @OneToMany(mappedBy = "drawResult", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("poolType ASC, position ASC")
    @Builder.Default
    private List<DrawPick> picks = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false, length = 16)
    @Builder.Default
    private DrawOrigin origin = DrawOrigin.OFFICIAL;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();

        if (createdAt == null) createdAt = now;

        if (cachedUntil == null) {
            cachedUntil = OffsetDateTime.now(ZoneOffset.UTC)
                    .plusMonths(4)
                    .toInstant();
        }
    }

}
