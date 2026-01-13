package com.lotteryapp.lottery.dto.group.response;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GetTicketGroupsResponse {

    private List<TicketGroupResponse> groups;
}