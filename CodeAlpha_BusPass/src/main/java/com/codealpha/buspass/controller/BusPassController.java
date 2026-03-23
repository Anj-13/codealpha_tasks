package com.codealpha.buspass.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.codealpha.buspass.dto.BookTicketRequest;
import com.codealpha.buspass.dto.RouteResponse;
import com.codealpha.buspass.dto.ScaleMetricsResponse;
import com.codealpha.buspass.dto.TicketResponse;
import com.codealpha.buspass.service.ScaleService;
import com.codealpha.buspass.service.TicketService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/buspass")
@Validated
public class BusPassController {

    private final TicketService ticketService;
    private final ScaleService scaleService;

    public BusPassController(TicketService ticketService, ScaleService scaleService) {
        this.ticketService = ticketService;
        this.scaleService = scaleService;
    }

    @GetMapping("/routes")
    public ResponseEntity<List<RouteResponse>> routes() {
        return ResponseEntity.ok(ticketService.getRoutes());
    }

    @PostMapping("/tickets/book")
    public ResponseEntity<TicketResponse> book(@Valid @RequestBody BookTicketRequest request) {
        return ResponseEntity.ok(ticketService.bookTicket(request));
    }

    @GetMapping("/tickets/{ticketNumber}")
    public ResponseEntity<TicketResponse> ticket(@PathVariable String ticketNumber) {
        return ResponseEntity.ok(ticketService.getByTicketNumber(ticketNumber));
    }

    @GetMapping("/tickets/{ticketNumber}/verify")
    public ResponseEntity<Map<String, Object>> verify(@PathVariable String ticketNumber) {
        boolean valid = ticketService.verifyTicket(ticketNumber);
        return ResponseEntity.ok(Map.of("ticketNumber", ticketNumber, "valid", valid));
    }

    @GetMapping("/metrics/scale")
    public ResponseEntity<ScaleMetricsResponse> scaleMetrics() {
        return ResponseEntity.ok(scaleService.calculate(ticketService.totalBookings()));
    }
}
