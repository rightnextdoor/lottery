package com.lotteryapp.lottery.domain.gamemode;

import com.lotteryapp.lottery.domain.jurisdiction.Jurisdiction;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Set;

@Entity
@Table(
        name = "game_mode",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_game_mode_key", columnNames = {"mode_key"})
        }
)
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameMode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mode_key", nullable = false, length = 60)
    private String modeKey;

    @Column(nullable = false, length = 80)
    private String displayName;

    @OneToOne(optional = true, cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "rules_id", nullable = true)
    private Rules rules;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 15)
    private GameScope scope;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "jurisdiction_code")
    private Jurisdiction jurisdiction;

    @Column(name = "tier_range_start_date")
    private LocalDate tierRangeStartDate;

    @Column(name = "tier_range_end_date")
    private LocalDate tierRangeEndDate;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "game_mode_draw_day",
            joinColumns = @JoinColumn(name = "game_mode_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "draw_day", nullable = false, length = 10)
    @Builder.Default
    private Set<DrawDay> drawDays = EnumSet.noneOf(DrawDay.class);

    @Column(name = "next_draw_date")
    private LocalDate nextDrawDate;

    @Column(name = "draw_time_local")
    private LocalTime drawTimeLocal;

    @Column(name = "draw_time_zone_id", length = 60)
    private String drawTimeZoneId;

    @Column(name = "latest_draw_date")
    private LocalDate latestDrawDate;

    @Column(name = "latest_white_winning_csv", length = 200)
    private String latestWhiteWinningCsv;

    @Column(name = "latest_red_winning_csv", length = 80)
    private String latestRedWinningCsv;

    @Column(name = "latest_jackpot_amount", precision = 18, scale = 2)
    private BigDecimal latestJackpotAmount;

    @Column(name = "latest_cash_value", precision = 18, scale = 2)
    private BigDecimal latestCashValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    @Builder.Default
    private GameModeStatus status = GameModeStatus.UP_TO_DATE;

    @PrePersist
    @PreUpdate
    void validateScopeAndJurisdiction() {
        if (scope == null) {
            throw new IllegalStateException("GameMode.scope is required.");
        }

        if (scope == GameScope.MULTI_STATE) {
            if (jurisdiction != null) {
                throw new IllegalStateException("MULTI_STATE games must not have a jurisdiction.");
            }
        } else { // STATE_ONLY
            if (jurisdiction == null) {
                throw new IllegalStateException("STATE_ONLY games must have a jurisdiction.");
            }
        }

        // Draw time + timezone should be set together (or both null).
        boolean hasTime = drawTimeLocal != null;
        boolean hasZone = drawTimeZoneId != null && !drawTimeZoneId.isBlank();

        if (hasTime != hasZone) {
            throw new IllegalStateException("GameMode.drawTimeLocal and GameMode.drawTimeZoneId must be set together (or both null).");
        }

        if (status == null) {
            throw new IllegalStateException("GameMode.status is required.");
        }
    }
}
