package com.lotteryapp.lottery.dto.source.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ListSourcesRequest {


    private String stateCode;

    private Long gameModeId;

    private Boolean enabled;

    private Integer pageNumber;
    private Integer pageSize;

    private String sort;
}
