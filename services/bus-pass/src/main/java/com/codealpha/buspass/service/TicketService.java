package com.codealpha.buspass.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.codealpha.buspass.dto.BookTicketRequest;
import com.codealpha.buspass.dto.RouteResponse;
import com.codealpha.buspass.dto.TicketResponse;
import com.codealpha.buspass.dto.TicketVerificationResponse;
import com.codealpha.buspass.exception.DuplicateBookingException;
import com.codealpha.buspass.exception.NotFoundException;
import com.codealpha.buspass.integration.PassengerCheck;
import com.codealpha.buspass.integration.RemovalSystemClient;
import com.codealpha.buspass.model.BookingStatus;
import com.codealpha.buspass.model.BusTicket;
import com.codealpha.buspass.model.Route;
import com.codealpha.buspass.model.ValidationStatus;
import com.codealpha.buspass.repository.BusTicketRepository;
import com.codealpha.buspass.repository.RouteRepository;

@Service
public class TicketService {

    private final RouteRepository routeRepository;
    private final BusTicketRepository busTicketRepository;
    private final PricingService pricingService;
    private final RemovalSystemClient removalSystemClient;
    private final TransactionTemplate transactions;

    public TicketService(RouteRepository routeRepository,
                         BusTicketRepository busTicketRepository,
                         PricingService pricingService,
                         RemovalSystemClient removalSystemClient,
                         TransactionTemplate transactions) {
        this.routeRepository = routeRepository;
        this.busTicketRepository = busTicketRepository;
        this.pricingService = pricingService;
        this.removalSystemClient = removalSystemClient;
        this.transactions = transactions;
    }

    public List<RouteResponse> getRoutes() {
        return routeRepository.findAll().stream()
            .map(r -> new RouteResponse(r.getCode(), r.getSource(), r.getDestination(), r.getBaseFare()))
            .toList();
    }

    /**
     * Calls to the redundancy service stay outside the transaction, so a slow service cannot
     * hold a database lock, and a passenger is only stored once their ticket exists.
     */
    public TicketResponse bookTicket(BookTicketRequest request) {
        Route route = routeRepository.findByCodeIgnoreCase(request.getRouteCode())
            .orElseThrow(() -> new NotFoundException("Route not found: " + request.getRouteCode()));

        String name = request.getPassengerName().trim();
        String email = request.getEmail().trim().toLowerCase();
        String phone = request.getPhone().trim();
        String bookingKey = pricingService.bookingKey(email, name, route.getCode(), request.getTravelDate());

        if (busTicketRepository.existsByBookingKey(bookingKey)) {
            throw new DuplicateBookingException();
        }

        PassengerCheck check = removalSystemClient.validate(name, email, phone);
        ValidationStatus initialStatus = toValidationStatus(check);

        BusTicket saved;
        try {
            saved = transactions.execute(tx -> issueTicket(request, route.getId(), name, email, phone, bookingKey, initialStatus));
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateBookingException();
        }

        if (check == PassengerCheck.UNIQUE) {
            ValidationStatus finalStatus = toValidationStatus(removalSystemClient.store(name, email, phone));
            busTicketRepository.updateValidationStatus(saved.getId(), finalStatus);
            saved.setValidationStatus(finalStatus);
        }

        return map(saved);
    }

    public TicketResponse lookup(String ticketNumber, String email) {
        // A wrong email gets the same answer as a wrong number, so neither can be probed.
        return busTicketRepository.findByTicketNumber(ticketNumber.trim())
            .filter(ticket -> ticket.getEmail().equalsIgnoreCase(email.trim()))
            .map(this::map)
            .orElseThrow(() -> new NotFoundException("Ticket not found"));
    }

    public TicketVerificationResponse verifyTicket(String ticketNumber, String token) {
        BusTicket ticket = busTicketRepository.findByTicketNumber(ticketNumber)
            .orElseThrow(() -> new NotFoundException("Ticket not found"));

        return new TicketVerificationResponse(ticketNumber, verificationResult(ticket, token));
    }

    public int totalBookings() {
        return Math.toIntExact(busTicketRepository.count());
    }

    @Scheduled(initialDelayString = "${integration.removal.retry-delay-ms:30000}",
        fixedDelayString = "${integration.removal.retry-delay-ms:30000}")
    public void retryPendingValidations() {
        for (BusTicket ticket : busTicketRepository.findTop50ByValidationStatusOrderByIdAsc(ValidationStatus.PENDING)) {
            PassengerCheck result = removalSystemClient.store(ticket.getPassengerName(), ticket.getEmail(), ticket.getPhone());
            if (result == PassengerCheck.UNAVAILABLE || result == PassengerCheck.DISABLED) {
                return;
            }
            busTicketRepository.updateValidationStatus(ticket.getId(), toValidationStatus(result));
        }
    }

    private BusTicket issueTicket(BookTicketRequest request, Long routeId, String name, String email, String phone,
                                  String bookingKey, ValidationStatus validationStatus) {
        // Locking the route makes bookings for it take turns, so each one sees the last seat taken.
        Route route = routeRepository.findByIdForUpdate(routeId)
            .orElseThrow(() -> new NotFoundException("Route not found: " + request.getRouteCode()));
        int seatNumber = busTicketRepository.findHighestSeat(routeId, request.getTravelDate()) + 1;

        BigDecimal fare = pricingService.calculateFare(route, request.getPassengerCategory());
        String ticketNumber = UUID.randomUUID().toString();
        String qrToken = pricingService.token(ticketNumber, email, request.getTravelDate().toString());
        String checksum = pricingService.checksum(ticketNumber, route.getCode(), fare, "GBP");

        return busTicketRepository.save(new BusTicket(
            ticketNumber,
            qrToken,
            name,
            email,
            phone,
            route,
            request.getTravelDate(),
            seatNumber,
            fare,
            "GBP",
            checksum,
            request.getPassengerCategory(),
            BookingStatus.ACTIVE,
            validationStatus,
            bookingKey
        ));
    }

    private String verificationResult(BusTicket ticket, String token) {
        if (ticket.getSignatureVersion() == null) {
            return TicketVerificationResponse.LEGACY_UNSIGNED;
        }
        if (ticket.getStatus() != BookingStatus.ACTIVE) {
            return TicketVerificationResponse.NOT_ACTIVE;
        }

        String expectedChecksum = pricingService.checksum(
            ticket.getTicketNumber(),
            ticket.getRoute().getCode(),
            ticket.getFarePaid(),
            ticket.getCurrency()
        );
        String expectedToken = pricingService.token(
            ticket.getTicketNumber(),
            ticket.getEmail(),
            ticket.getTravelDate().toString()
        );

        boolean signed = pricingService.matches(expectedChecksum, ticket.getPricingChecksum())
            && pricingService.matches(expectedToken, ticket.getQrToken());
        if (!signed) {
            return TicketVerificationResponse.TAMPERED;
        }
        if (token != null && !pricingService.matches(expectedToken, token)) {
            return TicketVerificationResponse.TOKEN_MISMATCH;
        }
        return TicketVerificationResponse.VALID;
    }

    private ValidationStatus toValidationStatus(PassengerCheck check) {
        return switch (check) {
            case KNOWN -> ValidationStatus.VERIFIED;
            case FLAGGED -> ValidationStatus.REVIEW;
            case UNIQUE, UNAVAILABLE -> ValidationStatus.PENDING;
            case DISABLED -> ValidationStatus.NOT_CHECKED;
        };
    }

    private TicketResponse map(BusTicket saved) {
        TicketResponse response = new TicketResponse();
        response.setId(saved.getId());
        response.setTicketNumber(saved.getTicketNumber());
        response.setQrToken(saved.getQrToken());
        response.setPassengerName(saved.getPassengerName());
        response.setEmail(saved.getEmail());
        response.setPhone(saved.getPhone());
        response.setRouteCode(saved.getRoute().getCode());
        response.setRouteName(saved.getRoute().getSource() + " -> " + saved.getRoute().getDestination());
        response.setTravelDate(saved.getTravelDate());
        response.setSeatNumber(saved.getSeatNumber());
        response.setFarePaid(saved.getFarePaid());
        response.setCurrency(saved.getCurrency());
        response.setStatus(saved.getStatus());
        response.setValidationStatus(saved.getValidationStatus() == null
            ? ValidationStatus.NOT_CHECKED
            : saved.getValidationStatus());
        response.setCreatedAt(saved.getCreatedAt());
        return response;
    }
}
