package com.codealpha.chatbot.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.codealpha.chatbot.model.AnswerSource;
import com.codealpha.chatbot.model.ChatResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

class ChatServiceTest {

    private static final String TICKET = "123e4567-e89b-12d3-a456-426614174000";
    private static final String ROUTES = """
        [{"code":"R-100","source":"Leeds","destination":"Manchester","baseFare":12.50}]""";

    private HttpServer stub;
    private ChatService chat;
    private volatile String lastBody;

    @BeforeEach
    void setUp() throws IOException {
        stub = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        stub.createContext("/api/buspass/routes", exchange -> reply(exchange, 200, ROUTES));
        stub.createContext("/api/buspass/metrics/scale", exchange ->
            reply(exchange, 200, "{\"totalBookings\":3,\"recommendedServers\":1,\"maxServers\":20}"));
        stub.createContext("/api/buspass/tickets/lookup", this::handleLookup);
        stub.createContext("/api/records", exchange -> reply(exchange, 200, """
            [{"fullName":"A*** T***","email":"a***@example.com","status":"UNIQUE"}]"""));
        stub.createContext("/api/records/validate", this::handleValidate);
        stub.start();

        chat = chatAgainst("http://localhost:" + stub.getAddress().getPort());
    }

    @AfterEach
    void tearDown() {
        stub.stop(0);
    }

    private static ChatService chatAgainst(String host) {
        return new ChatService(new IntentService(),
            new ExternalKnowledgeService(new ObjectMapper(), host + "/api/buspass", host + "/api/records"));
    }

    private void handleLookup(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (body.contains("pax@example.com")) {
            reply(exchange, 200, """
                {"status":"ACTIVE","routeName":"Leeds -> Manchester","travelDate":"2030-01-15",
                 "seatNumber":4,"validationStatus":"PENDING"}""");
        } else {
            reply(exchange, 404, "{\"message\":\"Ticket not found\"}");
        }
    }

    private void handleValidate(HttpExchange exchange) throws IOException {
        lastBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        reply(exchange, 200, "{\"status\":\"REDUNDANT\",\"validationReason\":\"Exact duplicate detected from fingerprint\"}");
    }

    private static void reply(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    @Test
    void staticAnswersAreLabelledAsStatic() {
        ChatResponse response = chat.answer("how does validation work");

        assertEquals("VALIDATION_HELP", response.getIntent());
        assertEquals(AnswerSource.STATIC, response.getSource());
    }

    @Test
    void liveAnswersAreLabelledAsLive() {
        ChatResponse response = chat.answer("show routes");

        assertEquals(AnswerSource.LIVE, response.getSource());
        assertTrue(response.getResponse().contains("R-100 Leeds -> Manchester"));
    }

    @Test
    void fareAnswerCombinesTheRulesWithCurrentFares() {
        String response = chat.answer("what are the fares").getResponse();

        assertTrue(response.contains("80%"));
        assertTrue(response.contains("£12.50"));
    }

    @Test
    void bookingLookupNeedsBothDetails() {
        ChatResponse response = chat.answer("check booking " + TICKET);

        assertEquals("BOOKING_LOOKUP", response.getIntent());
        assertEquals(StaticResponses.LOOKUP_NEEDS_DETAILS, response.getResponse());
        assertEquals(AnswerSource.STATIC, response.getSource());
    }

    @Test
    void bookingLookupReportsStatusInPlainWords() {
        String response = chat.answer("check booking " + TICKET + " pax@example.com").getResponse();

        assertTrue(response.contains("seat 4"));
        assertTrue(response.contains("pending"));
    }

    @Test
    void wrongEmailRevealsNothing() {
        String response = chat.answer("check booking " + TICKET + " stranger@example.com").getResponse();

        assertTrue(response.startsWith("No booking matches"));
        assertFalse(response.contains("Manchester"));
    }

    @Test
    void validationCheckSendsTheParsedDetails() {
        String response = chat.answer("validate Ava Thompson ava@example.com 07123456789").getResponse();

        assertTrue(response.startsWith("REDUNDANT"));
        assertTrue(lastBody.contains("Ava Thompson"));
        assertTrue(response.endsWith("Nothing was stored."));
    }

    @Test
    void validationCheckAsksForMissingDetails() {
        assertEquals(StaticResponses.CHECK_NEEDS_DETAILS, chat.answer("validate ava@example.com").getResponse());
    }

    @Test
    void repeatsMaskedRecordsOnly() {
        String response = chat.answer("show redundancy records").getResponse();

        assertTrue(response.contains("a***@example.com"));
    }

    @Test
    void everyLiveLookupSurvivesAnOutage() {
        ChatService offline = chatAgainst("http://localhost:1");

        assertTrue(offline.answer("show routes").getResponse().contains("could not reach"));
        assertTrue(offline.answer("server scale status").getResponse().contains("unavailable"));
        assertTrue(offline.answer("show redundancy records").getResponse().contains("could not read"));
        assertTrue(offline.answer("check booking " + TICKET + " pax@example.com").getResponse().contains("could not reach"));
        assertTrue(offline.answer("validate Ava ava@example.com 07123456789").getResponse().contains("could not reach"));
        assertTrue(offline.answer("what are the fares").getResponse().contains("80%"));
    }

    @Test
    void unknownTextGetsSuggestions() {
        ChatResponse response = chat.answer("purple monkey dishwasher");

        assertEquals("FALLBACK", response.getIntent());
        assertTrue(response.getResponse().contains("help"));
    }
}
