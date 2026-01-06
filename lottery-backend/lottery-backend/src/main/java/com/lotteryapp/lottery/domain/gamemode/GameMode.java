package com.lotteryapp.lottery.domain.gamemode;

import com.lotteryapp.lottery.domain.jurisdiction.Jurisdiction;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
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

    /**
     * Stable key used in URLs / code (ex: "POWERBALL", "MEGA_MILLIONS", "IL_LOTTO").
     */
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

    @Column(name = "latest_draw_date")
    private LocalDate latestDrawDate;

    @Column(name = "latest_white_winning_csv", length = 200)
    private String latestWhiteWinningCsv;

    @Column(name = "latest_red_winning_csv", length = 80)
    private String latestRedWinningCsv;

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
    }
}
