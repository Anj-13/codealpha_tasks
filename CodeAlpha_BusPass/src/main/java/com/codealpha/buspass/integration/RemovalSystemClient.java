package com.codealpha.buspass.integration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class RemovalSystemClient {

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

    public void syncPassenger(String fullName, String email) {
        if (!enabled) {
            return;
        }

        try {
            Map<String, Object> payload = Map.of(
                "fullName", fullName,
                "email", email,
                "phone", "0000000000",
                "address", "buspass-generated"
            );

            String body = mapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl))
                .timeout(Duration.ofSeconds(3))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int code = response.statusCode();
            if (code >= 400) {
                Map<String, Object> data = mapper.readValue(response.body(), new TypeReference<>() {});
                Object reason = data.get("validationReason");
                if (reason != null) {
                    // Keep bus booking resilient if the dedupe service marks record as redundant.
                    return;
                }
            }
        } catch (Exception ignored) {
            // If Task 1 service is down, booking flow continues to preserve availability.
        }
    }
}
