package com.lotteryapp.lottery.domain.source;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "source",
        indexes = {
                @Index(name = "idx_source_state_game_enabled_priority", columnList = "state_code,game_mode_id,enabled,priority"),
                @Index(name = "idx_source_game_enabled", columnList = "game_mode_id,enabled")
        }
)
public class Source {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "state_code", nullable = false, length = 16)
    private String stateCode;


    @Column(name = "game_mode_id", nullable = false)
    private Long gameModeId;


    @Column(name = "priority", nullable = false)
    private int priority;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;


    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 16)
    private SourceType sourceType;


    @Column(name = "parser_key", nullable = false, length = 80)
    private String parserKey;


    @Column(name = "url_template", nullable = false, length = 1000)
    private String urlTemplate;

    // Capability flags (what this source can do)

    @Column(name = "supports_game_list", nullable = false)
    private boolean supportsGameList;

    @Column(name = "draw_latest", nullable = false)
    private boolean drawLatest;

    @Column(name = "draw_by_date", nullable = false)
    private boolean drawByDate;

    @Column(name = "draw_history", nullable = false)
    private boolean drawHistory;

    @Column(name = "supports_rules", nullable = false)
    private boolean supportsRules;

    @Column(name = "supports_schedule", nullable = false)
    private boolean supportsSchedule;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
