package com.codealpha.buspass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.codealpha.buspass.integration.PassengerCheck;
import com.codealpha.buspass.integration.RemovalSystemClient;
import com.codealpha.buspass.repository.BusTicketRepository;
import com.codealpha.buspass.service.TicketService;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class TicketApiTests {

    private static final String TRAVEL_DATE = LocalDate.now().plusDays(30).toString();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private BusTicketRepository tickets;

    @Autowired
    private TicketService ticketService;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private RemovalSystemClient removalSystem;

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        tickets.deleteAll();
        when(removalSystem.validate(any(), any(), any())).thenReturn(PassengerCheck.UNIQUE);
        when(removalSystem.store(any(), any(), any())).thenReturn(PassengerCheck.KNOWN);
    }

    private static String booking(String name, String email) {
        return """
            {"passengerName":"%s","email":"%s","phone":"07000 100 001","routeCode":"R-100",
             "travelDate":"%s","passengerCategory":"STUDENT"}""".formatted(name, email, TRAVEL_DATE);
    }

    private ResultActions book(String body) throws Exception {
        return mvc.perform(post("/api/buspass/tickets/book").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String bookAndGetNumber(String name, String email) throws Exception {
        String body = book(booking(name, email)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("ticketNumber").asText();
    }

    private ResultActions lookup(String ticketNumber, String email) throws Exception {
        return mvc.perform(post("/api/buspass/tickets/lookup").contentType(MediaType.APPLICATION_JSON)
            .content("{\"ticketNumber\":\"%s\",\"email\":\"%s\"}".formatted(ticketNumber, email)));
    }

    private ResultActions verifyTicket(String ticketNumber) throws Exception {
        return mvc.perform(get("/api/buspass/tickets/{ticketNumber}/verify", ticketNumber));
    }

    @Test
    void booksATicketAtTheCategoryFare() throws Exception {
        book(booking("Pax One", "pax.one@example.com"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.farePaid").value(10.00))
            .andExpect(jsonPath("$.seatNumber").value(1))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.validationStatus").value("VERIFIED"));
    }

    @Test
    void storesThePassengerOnlyAfterValidatingWithTheRealPhone() throws Exception {
        bookAndGetNumber("Pax One", "pax.one@example.com");

        InOrder order = inOrder(removalSystem);
        order.verify(removalSystem).validate("Pax One", "pax.one@example.com", "07000 100 001");
        order.verify(removalSystem).store("Pax One", "pax.one@example.com", "07000 100 001");
    }

    @Test
    void returningPassengerIsNotStoredAgain() throws Exception {
        when(removalSystem.validate(any(), any(), any())).thenReturn(PassengerCheck.KNOWN);

        book(booking("Pax One", "pax.one@example.com"))
            .andExpect(jsonPath("$.validationStatus").value("VERIFIED"));

        verify(removalSystem, never()).store(any(), any(), any());
    }

    @Test
    void partialMatchIsMarkedForReviewAndStillBooked() throws Exception {
        when(removalSystem.validate(any(), any(), any())).thenReturn(PassengerCheck.FLAGGED);

        book(booking("Pax One", "pax.one@example.com"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.validationStatus").value("REVIEW"));
    }

    @Test
    void outageLeavesTheTicketPendingUntilARetrySucceeds() throws Exception {
        when(removalSystem.validate(any(), any(), any())).thenReturn(PassengerCheck.UNAVAILABLE);
        when(removalSystem.store(any(), any(), any())).thenReturn(PassengerCheck.UNAVAILABLE);

        String number = bookAndGetNumber("Pax One", "pax.one@example.com");
        lookup(number, "pax.one@example.com").andExpect(jsonPath("$.validationStatus").value("PENDING"));

        ticketService.retryPendingValidations();
        lookup(number, "pax.one@example.com").andExpect(jsonPath("$.validationStatus").value("PENDING"));

        when(removalSystem.store(any(), any(), any())).thenReturn(PassengerCheck.KNOWN);
        ticketService.retryPendingValidations();
        lookup(number, "pax.one@example.com").andExpect(jsonPath("$.validationStatus").value("VERIFIED"));
    }

    @Test
    void switchedOffIntegrationIsNotReportedAsVerified() throws Exception {
        when(removalSystem.validate(any(), any(), any())).thenReturn(PassengerCheck.DISABLED);

        book(booking("Pax One", "pax.one@example.com"))
            .andExpect(jsonPath("$.validationStatus").value("NOT_CHECKED"));
    }

    @Test
    void rejectsTheSameBookingTwiceIgnoringFormatting() throws Exception {
        book(booking("Pax One", "pax.one@example.com")).andExpect(status().isCreated());

        book(booking("Pax One", "pax.one@example.com")).andExpect(status().isConflict());
        book(booking("  PAX   one ", "PAX.ONE@example.com"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").exists());
        assertThat(tickets.count()).isEqualTo(1);
    }

    @Test
    void duplicateIsCaughtBeforeTheRedundancyServiceIsCalled() throws Exception {
        book(booking("Pax One", "pax.one@example.com"));
        book(booking("Pax One", "pax.one@example.com"));

        verify(removalSystem).validate(eq("Pax One"), any(), any());
    }

    @Test
    void seatsCountUpFromTheDatabase() throws Exception {
        book(booking("Pax One", "pax.one@example.com")).andExpect(jsonPath("$.seatNumber").value(1));
        book(booking("Pax Two", "pax.two@example.com")).andExpect(jsonPath("$.seatNumber").value(2));

        // Removing a lower seat must not hand out the highest one again.
        jdbc.update("delete from bus_tickets where seat_number = 1");
        book(booking("Pax Three", "pax.three@example.com")).andExpect(jsonPath("$.seatNumber").value(3));
    }

    @Test
    void rejectsInvalidBookings() throws Exception {
        book("""
            {"passengerName":"Pax","email":"x@example.com","routeCode":"R-100",
             "travelDate":"2020-01-01","passengerCategory":"ADULT"}""")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors.phone").value("Phone is required"))
            .andExpect(jsonPath("$.fieldErrors.travelDate").value("Travel date cannot be in the past"));

        book(booking("Pax", "x@example.com").replace("STUDENT", "PILOT"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Request body is missing or malformed"));

        book(booking("Pax", "x@example.com").replace("R-100", "LON-MAN"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Route not found: LON-MAN"));
    }

    @Test
    void lookupNeedsTheBookingEmail() throws Exception {
        String number = bookAndGetNumber("Pax One", "pax.one@example.com");

        lookup(number, "PAX.ONE@example.com")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.routeName").value("Leeds -> Manchester"));
        lookup(number, "stranger@example.com")
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Ticket not found"));
        lookup("no-such-ticket", "pax.one@example.com")
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Ticket not found"));
    }

    @Test
    void ticketIsNotReadableByNumberAlone() throws Exception {
        String number = bookAndGetNumber("Pax One", "pax.one@example.com");

        mvc.perform(get("/api/buspass/tickets/{ticketNumber}", number)).andExpect(status().isNotFound());
    }

    @Test
    void verifiesAnUntouchedTicket() throws Exception {
        String number = bookAndGetNumber("Pax One", "pax.one@example.com");

        verifyTicket(number)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.valid").value(true))
            .andExpect(jsonPath("$.reason").value("VALID"));
    }

    @Test
    void detectsAChangedFare() throws Exception {
        String number = bookAndGetNumber("Pax One", "pax.one@example.com");

        jdbc.update("update bus_tickets set fare_paid = 0.01 where ticket_number = ?", number);

        verifyTicket(number)
            .andExpect(jsonPath("$.valid").value(false))
            .andExpect(jsonPath("$.reason").value("TAMPERED"));
    }

    @Test
    void detectsAChangedTravelDate() throws Exception {
        String number = bookAndGetNumber("Pax One", "pax.one@example.com");

        jdbc.update("update bus_tickets set travel_date = travel_date + 1 where ticket_number = ?", number);

        verifyTicket(number).andExpect(jsonPath("$.reason").value("TAMPERED"));
    }

    @Test
    void wrongQrTokenFails() throws Exception {
        String number = bookAndGetNumber("Pax One", "pax.one@example.com");

        mvc.perform(get("/api/buspass/tickets/{ticketNumber}/verify", number).param("token", "forged"))
            .andExpect(jsonPath("$.valid").value(false))
            .andExpect(jsonPath("$.reason").value("TOKEN_MISMATCH"));
    }

    @Test
    void ticketsFromBeforeSigningAreNeverReportedAsValid() throws Exception {
        String number = bookAndGetNumber("Pax One", "pax.one@example.com");

        jdbc.update("update bus_tickets set signature_version = null, validation_status = null where ticket_number = ?", number);

        verifyTicket(number)
            .andExpect(jsonPath("$.valid").value(false))
            .andExpect(jsonPath("$.reason").value("LEGACY_UNSIGNED"));
        lookup(number, "pax.one@example.com").andExpect(jsonPath("$.validationStatus").value("NOT_CHECKED"));
    }

    @Test
    void unknownTicketCannotBeVerified() throws Exception {
        verifyTicket("no-such-ticket").andExpect(status().isNotFound());
    }

    @Test
    void healthEndpointIsAvailable() throws Exception {
        mvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }
}
