package com.lotteryapp.lottery.controller;

import com.lotteryapp.lottery.dto.group.request.*;
import com.lotteryapp.lottery.dto.group.response.*;
import com.lotteryapp.lottery.service.TicketGroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ticket-groups")
public class TicketGroupController {

    private final TicketGroupService ticketGroupService;

    @PostMapping("/list")
    public GetTicketGroupsResponse list(@Valid @RequestBody GetTicketGroupsRequest request) {
        return ticketGroupService.getAllForGame(request);
    }

    @PostMapping("/get")
    public GetTicketGroupResponse get(@Valid @RequestBody GetTicketGroupRequest request) {
        return ticketGroupService.getById(request);
    }

    @PostMapping("/create")
    public CreateTicketGroupResponse create(@Valid @RequestBody CreateTicketGroupRequest request) {
        return ticketGroupService.create(request);
    }

    @PostMapping("/update")
    public UpdateTicketGroupResponse update(@Valid @RequestBody UpdateTicketGroupRequest request) {
        return ticketGroupService.update(request);
    }

    @PostMapping("/delete")
    public DeleteTicketGroupResponse delete(@Valid @RequestBody DeleteTicketGroupRequest request) {
        return ticketGroupService.delete(request);
    }
}