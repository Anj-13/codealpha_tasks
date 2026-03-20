package com.codealpha.buspass.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.codealpha.buspass.dto.BookTicketRequest;
import com.codealpha.buspass.dto.RouteResponse;
import com.codealpha.buspass.dto.TicketResponse;
import com.codealpha.buspass.integration.RemovalSystemClient;
import com.codealpha.buspass.model.BookingStatus;
import com.codealpha.buspass.model.BusTicket;
import com.codealpha.buspass.model.Route;
import com.codealpha.buspass.repository.BusTicketRepository;
import com.codealpha.buspass.repository.RouteRepository;

@Service
public class TicketService {

    private final RouteRepository routeRepository;
    private final BusTicketRepository busTicketRepository;
    private final PricingService pricingService;
    private final RemovalSystemClient removalSystemClient;

    private final ConcurrentHashMap<String, AtomicInteger> seatCounters = new ConcurrentHashMap<>();

    public TicketService(RouteRepository routeRepository,
                         BusTicketRepository busTicketRepository,
                         PricingService pricingService,
                         RemovalSystemClient removalSystemClient) {
        this.routeRepository = routeRepository;
        this.busTicketRepository = busTicketRepository;
        this.pricingService = pricingService;
        this.removalSystemClient = removalSystemClient;
    }

    public List<RouteResponse> getRoutes() {
        return routeRepository.findAll().stream()
            .map(r -> new RouteResponse(r.getCode(), r.getSource(), r.getDestination(), r.getBaseFare()))
            .toList();
    }

    @Transactional
    public TicketResponse bookTicket(BookTicketRequest request) {
        Route route = routeRepository.findByCodeIgnoreCase(request.getRouteCode())
            .orElseThrow(() -> new IllegalArgumentException("Route not found: " + request.getRouteCode()));

        BigDecimal fare = pricingService.calculateFare(route, request.getPassengerCategory());

        String ticketNumber = UUID.randomUUID().toString();
        int seatNumber = nextSeat(route.getCode(), request.getTravelDate());
        String qrToken = pricingService.token(ticketNumber, request.getEmail(), request.getTravelDate().toString());
        String checksum = pricingService.checksum(ticketNumber, route.getCode(), fare, "GBP");

        BusTicket entity = new BusTicket(
            ticketNumber,
            qrToken,
            request.getPassengerName().trim(),
            request.getEmail().trim().toLowerCase(),
            route,
            request.getTravelDate(),
            seatNumber,
            fare,
            "GBP",
            checksum,
            request.getPassengerCategory(),
            BookingStatus.ACTIVE
        );

        BusTicket saved = busTicketRepository.save(entity);
        removalSystemClient.syncPassenger(saved.getPassengerName(), saved.getEmail());
        return map(saved);
    }

    public TicketResponse getByTicketNumber(String ticketNumber) {
        BusTicket ticket = busTicketRepository.findByTicketNumber(ticketNumber)
            .orElseThrow(() -> new IllegalArgumentException("Ticket not found"));
        return map(ticket);
    }

    public boolean verifyTicket(String ticketNumber) {
        BusTicket ticket = busTicketRepository.findByTicketNumber(ticketNumber)
            .orElseThrow(() -> new IllegalArgumentException("Ticket not found"));

        String expected = pricingService.checksum(
            ticket.getTicketNumber(),
            ticket.getRoute().getCode(),
            ticket.getFarePaid(),
            ticket.getCurrency()
        );

        return expected.equals(ticket.getPricingChecksum()) && ticket.getStatus() == BookingStatus.ACTIVE;
    }

    public int totalBookings() {
        return Math.toIntExact(busTicketRepository.count());
    }

    private int nextSeat(String routeCode, LocalDate date) {
        String key = routeCode.toUpperCase() + "-" + date;
        AtomicInteger counter = seatCounters.computeIfAbsent(key, ignored -> new AtomicInteger(0));
        return counter.incrementAndGet();
    }

    private TicketResponse map(BusTicket saved) {
        TicketResponse response = new TicketResponse();
        response.setId(saved.getId());
        response.setTicketNumber(saved.getTicketNumber());
        response.setQrToken(saved.getQrToken());
        response.setPassengerName(saved.getPassengerName());
        response.setEmail(saved.getEmail());
        response.setRouteCode(saved.getRoute().getCode());
        response.setRouteName(saved.getRoute().getSource() + " -> " + saved.getRoute().getDestination());
        response.setTravelDate(saved.getTravelDate());
        response.setSeatNumber(saved.getSeatNumber());
        response.setFarePaid(saved.getFarePaid());
        response.setCurrency(saved.getCurrency());
        response.setStatus(saved.getStatus());
        response.setCreatedAt(saved.getCreatedAt());
        return response;
    }
}
