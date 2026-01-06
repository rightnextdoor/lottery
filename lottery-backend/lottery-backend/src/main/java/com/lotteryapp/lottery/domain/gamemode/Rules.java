package com.lotteryapp.lottery.domain.gamemode;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "rules")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Rules {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "format_start_date")
    private LocalDate formatStartDate;

    @Column(name = "white_min", nullable = false)
    private Integer whiteMin;

    @Column(name = "white_max", nullable = false)
    private Integer whiteMax;

    @Column(name = "white_pick_count", nullable = false)
    private Integer whitePickCount;

    @Column(name = "white_ordered", nullable = false)
    private Boolean whiteOrdered;

    @Column(name = "white_allow_repeats", nullable = false)
    private Boolean whiteAllowRepeats;

    @Column(name = "red_min")
    private Integer redMin;

    @Column(name = "red_max")
    private Integer redMax;

    @Column(name = "red_pick_count", nullable = false)
    private Integer redPickCount;

    @Column(name = "red_ordered", nullable = false)
    private Boolean redOrdered;

    @Column(name = "red_allow_repeats", nullable = false)
    private Boolean redAllowRepeats;

    @PrePersist
    @PreUpdate
    void normalizeDefaults() {
        // For games without a bonus pool, keep redPickCount = 0.
        if (redPickCount == null) redPickCount = 0;

        // Default behavior for typical lotto games:
        if (whiteOrdered == null) whiteOrdered = false;
        if (whiteAllowRepeats == null) whiteAllowRepeats = false;

        if (redOrdered == null) redOrdered = false;
        if (redAllowRepeats == null) redAllowRepeats = false;
    }
}
