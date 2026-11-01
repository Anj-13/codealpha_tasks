package com.codealpha.buspass;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.codealpha.buspass.integration.PassengerCheck;
import com.codealpha.buspass.integration.RemovalSystemClient;
import com.sun.net.httpserver.HttpServer;

class RemovalSystemClientTests {

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<String> requestPath = new AtomicReference<>();
    private volatile int responseStatus;
    private volatile String responseBody;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            requestPath.set(exchange.getRequestURI().getPath());
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(responseStatus, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort() + "/api/records";
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private void respondWith(int status, String body) {
        responseStatus = status;
        responseBody = body;
    }

    private RemovalSystemClient client() {
        return new RemovalSystemClient(true, baseUrl);
    }

    @Test
    void readsEachValidationOutcome() {
        respondWith(200, "{\"status\":\"UNIQUE\"}");
        assertThat(client().validate("Pax", "pax@example.com", "07000100001")).isEqualTo(PassengerCheck.UNIQUE);

        respondWith(200, "{\"status\":\"REDUNDANT\"}");
        assertThat(client().validate("Pax", "pax@example.com", "07000100001")).isEqualTo(PassengerCheck.KNOWN);

        respondWith(200, "{\"status\":\"FALSE_POSITIVE\"}");
        assertThat(client().validate("Pax", "pax@example.com", "07000100001")).isEqualTo(PassengerCheck.FLAGGED);
    }

    @Test
    void validatesAtTheNoWriteEndpointWithThePassengersRealPhone() {
        respondWith(200, "{\"status\":\"UNIQUE\"}");

        client().validate("Pax", "pax@example.com", "07000100001");

        assertThat(requestPath.get()).isEqualTo("/api/records/validate");
        assertThat(requestBody.get()).contains("07000100001").doesNotContain("0000000000");
    }

    @Test
    void refusedDataIsFlaggedNotRetried() {
        respondWith(400, "{\"message\":\"Validation failed\"}");

        assertThat(client().validate("Pax", "pax@example.com", "07000100001")).isEqualTo(PassengerCheck.FLAGGED);
    }

    @Test
    void readsEachStoreOutcome() {
        respondWith(201, "{}");
        assertThat(client().store("Pax", "pax@example.com", "07000100001")).isEqualTo(PassengerCheck.KNOWN);

        respondWith(409, "{}");
        assertThat(client().store("Pax", "pax@example.com", "07000100001")).isEqualTo(PassengerCheck.KNOWN);

        respondWith(422, "{}");
        assertThat(client().store("Pax", "pax@example.com", "07000100001")).isEqualTo(PassengerCheck.FLAGGED);
    }

    @Test
    void serverErrorsAndNonsenseCountAsUnavailable() {
        respondWith(500, "{}");
        assertThat(client().store("Pax", "pax@example.com", "07000100001")).isEqualTo(PassengerCheck.UNAVAILABLE);
        assertThat(client().validate("Pax", "pax@example.com", "07000100001")).isEqualTo(PassengerCheck.UNAVAILABLE);

        respondWith(200, "not json");
        assertThat(client().validate("Pax", "pax@example.com", "07000100001")).isEqualTo(PassengerCheck.UNAVAILABLE);
    }

    @Test
    void unreachableServiceCountsAsUnavailable() {
        server.stop(0);

        assertThat(client().validate("Pax", "pax@example.com", "07000100001")).isEqualTo(PassengerCheck.UNAVAILABLE);
    }

    @Test
    void disabledIntegrationMakesNoCalls() {
        RemovalSystemClient disabled = new RemovalSystemClient(false, baseUrl);

        assertThat(disabled.validate("Pax", "pax@example.com", "07000100001")).isEqualTo(PassengerCheck.DISABLED);
        assertThat(requestPath.get()).isNull();
    }
}
