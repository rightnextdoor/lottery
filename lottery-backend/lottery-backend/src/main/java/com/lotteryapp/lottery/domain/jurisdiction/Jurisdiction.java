package com.lotteryapp.lottery.domain.jurisdiction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "jurisdiction")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Jurisdiction {

    @Id
    @Column(length = 5, nullable = false)
    private String code;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false)
    private boolean enabled;
}
