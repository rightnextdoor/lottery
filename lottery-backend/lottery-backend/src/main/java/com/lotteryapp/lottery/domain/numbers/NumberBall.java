package com.lotteryapp.lottery.domain.numbers;

import com.lotteryapp.lottery.domain.gamemode.GameMode;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(
        name = "number_ball",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_number_ball_game_pool_value",
                        columnNames = {"game_mode_id", "pool_type", "number_value"}
                )
        },
        indexes = {
                @Index(name = "ix_number_ball_game_pool", columnList = "game_mode_id,pool_type")
        }
)
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NumberBall {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "game_mode_id", nullable = false)
    private GameMode gameMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "pool_type", nullable = false, length = 10)
    private PoolType poolType;

    @Column(name = "number_value", nullable = false)
    private Integer numberValue;

    @Column(name = "last_drawn_date")
    private LocalDate lastDrawnDate;

    @Column(name = "total_count", nullable = false)
    private Integer totalCount;

    @Column(name = "tier_count", nullable = false)
    private Integer tierCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Tier tier;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_change", nullable = false, length = 10)
    private StatusChange statusChange;

    @PrePersist
    void prePersist() {
        if (totalCount == null) totalCount = 0;
        if (tierCount == null) tierCount = 0;
        if (tier == null) tier = Tier.MID;
        if (statusChange == null) statusChange = StatusChange.NONE;
    }
}
