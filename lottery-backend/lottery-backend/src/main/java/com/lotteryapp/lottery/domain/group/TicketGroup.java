package com.lotteryapp.lottery.domain.group;

import com.lotteryapp.lottery.domain.gamemode.GameMode;
import com.lotteryapp.lottery.domain.numbers.PoolType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
        name = "ticket_group",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_group_count_combo",
                        columnNames = {"game_mode_id", "pool_type", "group_mode", "hot_count", "mid_count", "cold_count"}
                ),
                @UniqueConstraint(
                        name = "uk_group_percent_combo",
                        columnNames = {"game_mode_id", "pool_type", "group_mode", "hot_pct", "mid_pct", "cold_pct"}
                )
        },
        indexes = {
                @Index(name = "ix_group_game_mode", columnList = "game_mode_id"),
                @Index(name = "ix_group_pool_type", columnList = "pool_type"),
                @Index(name = "ix_group_key", columnList = "group_key")
        }
)
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "game_mode_id", nullable = false)
    private GameMode gameMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "pool_type", nullable = false, length = 10)
    private PoolType poolType;

    @Enumerated(EnumType.STRING)
    @Column(name = "group_mode", nullable = false, length = 10)
    private GroupMode groupMode;

    @Column(name = "group_key", nullable = false, length = 140)
    private String groupKey;

    @Column(name = "display_name", nullable = false, length = 180)
    private String displayName;

    @Column(name = "hot_count")
    private Integer hotCount;

    @Column(name = "mid_count")
    private Integer midCount;

    @Column(name = "cold_count")
    private Integer coldCount;

    @Column(name = "hot_pct")
    private Integer hotPct;

    @Column(name = "mid_pct")
    private Integer midPct;

    @Column(name = "cold_pct")
    private Integer coldPct;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public boolean isCountMode() {
        return groupMode == GroupMode.COUNT;
    }

    public boolean isPercentMode() {
        return groupMode == GroupMode.PERCENT;
    }
}
