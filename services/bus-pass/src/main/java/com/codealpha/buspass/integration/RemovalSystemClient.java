package com.codealpha.buspass.integration;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class RemovalSystemClient {

    private static final Logger log = LoggerFactory.getLogger(RemovalSystemClient.class);

    private final boolean enabled;
    private final String baseUrl;
    private final HttpClient client;
    private final ObjectMapper mapper;

    public RemovalSystemClient(@Value("${integration.removal.enabled:true}") boolean enabled,
                               @Value("${integration.removal.base-url:http://localhost:8081/api/records}") String baseUrl) {
        this.enabled = enabled;
        this.baseUrl = baseUrl;
        this.mapper = new ObjectMapper();
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    }

    /** Classifies a passenger without storing anything. */
    public PassengerCheck validate(String fullName, String email, String phone) {
        return post(baseUrl + "/validate", fullName, email, phone, this::readValidation);
    }

    /** Stores a passenger. Safe to repeat, because a repeat comes back as a duplicate. */
    public PassengerCheck store(String fullName, String email, String phone) {
        return post(baseUrl, fullName, email, phone, this::readStoreResult);
    }

    private PassengerCheck post(String url, String fullName, String email, String phone,
                                Function<HttpResponse<String>, PassengerCheck> interpret) {
        if (!enabled) {
            return PassengerCheck.DISABLED;
        }

        try {
            String body = mapper.writeValueAsString(Map.of("fullName", fullName, "email", email, "phone", phone));
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(3))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

            return interpret.apply(client.send(request, HttpResponse.BodyHandlers.ofString()));
        } catch (IOException ex) {
            log.warn("Redundancy service could not be reached: {}", ex.toString());
            return PassengerCheck.UNAVAILABLE;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return PassengerCheck.UNAVAILABLE;
        }
    }

    private PassengerCheck readValidation(HttpResponse<String> response) {
        if (response.statusCode() == 400) {
            return PassengerCheck.FLAGGED;
        }
        if (response.statusCode() != 200) {
            log.warn("Redundancy service returned {} for a validation", response.statusCode());
            return PassengerCheck.UNAVAILABLE;
        }

        try {
            return switch (mapper.readTree(response.body()).path("status").asText()) {
                case "UNIQUE" -> PassengerCheck.UNIQUE;
                case "REDUNDANT" -> PassengerCheck.KNOWN;
                case "FALSE_POSITIVE" -> PassengerCheck.FLAGGED;
                default -> PassengerCheck.UNAVAILABLE;
            };
        } catch (IOException ex) {
            log.warn("Redundancy service sent an unreadable validation: {}", ex.toString());
            return PassengerCheck.UNAVAILABLE;
        }
    }

    private PassengerCheck readStoreResult(HttpResponse<String> response) {
        return switch (response.statusCode()) {
            case 201, 409 -> PassengerCheck.KNOWN;
            case 400, 422 -> PassengerCheck.FLAGGED;
            default -> {
                log.warn("Redundancy service returned {} when storing a passenger", response.statusCode());
                yield PassengerCheck.UNAVAILABLE;
            }
        };
    }
}
