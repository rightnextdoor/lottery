package com.lotteryapp.lottery.dto.generatorspec.request;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeleteGeneratorSpecRequest {

    private Long gameModeId;
    private Long id;
}
