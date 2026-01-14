package com.lotteryapp.lottery.domain.batch;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(
        name = "batch_check_record",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_batch_check_record_batch_drawdate_spec",
                        columnNames = {"saved_batch_id", "draw_date", "spec_number"}
                )
        },
        indexes = {
                @Index(name = "ix_batch_check_record_batch", columnList = "saved_batch_id"),
                @Index(name = "ix_batch_check_record_draw_date", columnList = "draw_date"),
                @Index(name = "ix_batch_check_record_spec", columnList = "saved_batch_id,spec_number")
        }
)
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BatchCheckRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "saved_batch_id", nullable = false)
    private SavedBatch savedBatch;

    @Column(name = "draw_date", nullable = false)
    private LocalDate drawDate;

    @Column(name = "spec_number", nullable = false)
    private Integer specNumber;

    @Column(name = "white_group_id")
    private Long whiteGroupId;

    @Column(name = "red_group_id")
    private Long redGroupId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "pct_any_hit", nullable = false)
    private Double pctAnyHit;

    @Column(name = "pct_red_hit", nullable = false)
    private Double pctRedHit;

    @Lob
    @Column(name = "white_hit_pct_json", nullable = false)
    private String whiteHitPctJson;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        if (pctAnyHit == null) pctAnyHit = 0.0;
        if (pctRedHit == null) pctRedHit = 0.0;
        if (whiteHitPctJson == null) whiteHitPctJson = "{}";
        if (specNumber == null || specNumber < 1) throw new IllegalStateException("specNumber must be >= 1");
    }
}
