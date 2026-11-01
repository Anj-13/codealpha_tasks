package com.codealpha.buspass.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.codealpha.buspass.dto.BookTicketRequest;
import com.codealpha.buspass.dto.LookupTicketRequest;
import com.codealpha.buspass.dto.RouteResponse;
import com.codealpha.buspass.dto.ScaleMetricsResponse;
import com.codealpha.buspass.dto.TicketResponse;
import com.codealpha.buspass.dto.TicketVerificationResponse;
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
        return ResponseEntity.status(HttpStatus.CREATED).body(ticketService.bookTicket(request));
    }

    // A POST body keeps the passenger's email out of URLs and access logs.
    @PostMapping("/tickets/lookup")
    public ResponseEntity<TicketResponse> lookup(@Valid @RequestBody LookupTicketRequest request) {
        return ResponseEntity.ok(ticketService.lookup(request.getTicketNumber(), request.getEmail()));
    }

    @GetMapping("/tickets/{ticketNumber}/verify")
    public ResponseEntity<TicketVerificationResponse> verify(@PathVariable String ticketNumber,
                                                             @RequestParam(required = false) String token) {
        return ResponseEntity.ok(ticketService.verifyTicket(ticketNumber, token));
    }

    @GetMapping("/metrics/scale")
    public ResponseEntity<ScaleMetricsResponse> scaleMetrics() {
        return ResponseEntity.ok(scaleService.calculate(ticketService.totalBookings()));
    }
}
