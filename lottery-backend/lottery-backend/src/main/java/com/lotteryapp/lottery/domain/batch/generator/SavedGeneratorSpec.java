package com.lotteryapp.lottery.domain.batch.generator;

import com.lotteryapp.lottery.domain.gamemode.GameMode;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "generator_spec",
        indexes = {
                @Index(name = "idx_generator_spec_game_mode_id", columnList = "game_mode_id")
        }
)
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SavedGeneratorSpec {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_mode_id", nullable = false)
    private GameMode gameMode;

    @Column(name = "ticket_count", nullable = false)
    private int ticketCount;

    @Column(name = "white_group_id")
    private Long whiteGroupId;

    @Column(name = "red_group_id")
    private Long redGroupId;

    @Column(name = "exclude_last_draw_numbers", nullable = false)
    private boolean excludeLastDrawNumbers;
}
