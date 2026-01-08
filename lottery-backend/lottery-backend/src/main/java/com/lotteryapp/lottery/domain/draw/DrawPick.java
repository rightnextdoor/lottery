package com.lotteryapp.lottery.domain.draw;

import com.lotteryapp.lottery.domain.numbers.PoolType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "draw_pick",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_pick_draw_pool_position",
                        columnNames = {"draw_result_id", "pool_type", "position"}
                )
        },
        indexes = {
                @Index(name = "ix_draw_pick_draw", columnList = "draw_result_id")
        }
)
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DrawPick {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "draw_result_id", nullable = false)
    private DrawResult drawResult;

    @Enumerated(EnumType.STRING)
    @Column(name = "pool_type", nullable = false, length = 10)
    private PoolType poolType;

    @Column(nullable = false)
    private Integer position;

    @Column(name = "number_value", nullable = false)
    private Integer numberValue;
}
