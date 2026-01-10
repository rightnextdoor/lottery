package com.lotteryapp.lottery.domain.draw;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(
        name = "draw_conflict",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_conflict_draw_result", columnNames = {"draw_result_id"})
        },
        indexes = {
                @Index(name = "ix_conflict_game_date", columnList = "game_mode_id,draw_date"),
                @Index(name = "ix_conflict_ack", columnList = "acknowledged")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DrawConflict {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "draw_result_id", nullable = false)
    private DrawResult drawResult;

    @Column(name = "game_mode_id", nullable = false)
    private Long gameModeId;

    @Column(name = "draw_date", nullable = false)
    private LocalDate drawDate;

    @Column(name = "manual_white_json", columnDefinition = "TEXT")
    private String manualWhiteJson;

    @Column(name = "manual_red_json", columnDefinition = "TEXT")
    private String manualRedJson;

    @Column(name = "official_white_json", columnDefinition = "TEXT")
    private String officialWhiteJson;

    @Column(name = "official_red_json", columnDefinition = "TEXT")
    private String officialRedJson;

    @Column(name = "acknowledged", nullable = false)
    @Builder.Default
    private boolean acknowledged = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
