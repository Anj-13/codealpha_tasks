package com.codealpha.chatbot.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Live lookups against Tasks 1 and 3. Each one answers in plain text, even when a service is down. */
public class ExternalKnowledgeService {

    private final HttpClient client;
    private final ObjectMapper mapper;
    private final String buspassBase;
    private final String removalBase;

    public ExternalKnowledgeService(ObjectMapper mapper) {
        this(mapper,
            System.getenv().getOrDefault("BUSPASS_BASE_URL", "http://localhost:8082/api/buspass"),
            System.getenv().getOrDefault("REMOVAL_BASE_URL", "http://localhost:8081/api/records"));
    }

    public ExternalKnowledgeService(ObjectMapper mapper, String buspassBase, String removalBase) {
        this.mapper = mapper;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        this.buspassBase = buspassBase;
        this.removalBase = removalBase;
    }

    public String fetchRoutesSummary() {
        try {
            String json = get(buspassBase + "/routes");
            List<Map<String, Object>> routes = mapper.readValue(json, new TypeReference<>() {});
            if (routes.isEmpty()) {
                return "No routes are configured yet.";
            }
            StringBuilder sb = new StringBuilder("Available routes: ");
            int limit = Math.min(routes.size(), 3);
            for (int i = 0; i < limit; i++) {
                Map<String, Object> route = routes.get(i);
                if (i > 0) {
                    sb.append(" | ");
                }
                sb.append(route.get("code")).append(" ")
                    .append(route.get("source")).append(" -> ").append(route.get("destination"));
            }
            if (routes.size() > limit) {
                sb.append(" ... and ").append(routes.size() - limit).append(" more.");
            }
            return sb.toString();
        } catch (Exception ex) {
            return "I could not reach the bus pass service right now.";
        }
    }

    public String fetchFaresSummary() {
        try {
            String json = get(buspassBase + "/routes");
            List<Map<String, Object>> routes = mapper.readValue(json, new TypeReference<>() {});
            if (routes.isEmpty()) {
                return "No routes are configured yet.";
            }
            StringBuilder sb = new StringBuilder("Current adult fares:");
            for (Map<String, Object> route : routes) {
                sb.append("\n• ").append(route.get("code")).append(" ")
                    .append(route.get("source")).append(" -> ").append(route.get("destination"))
                    .append(": £").append(String.format(Locale.ROOT, "%.2f", ((Number) route.get("baseFare")).doubleValue()));
            }
            return sb.toString();
        } catch (Exception ex) {
            return "I could not reach the bus pass service for current fares.";
        }
    }

    public String fetchScaleSummary() {
        try {
            String json = get(buspassBase + "/metrics/scale");
            Map<String, Object> metrics = mapper.readValue(json, new TypeReference<>() {});
            return "Total bookings: " + metrics.get("totalBookings")
                + ", recommended active servers: " + metrics.get("recommendedServers")
                + ", max servers: " + metrics.get("maxServers") + ".";
        } catch (Exception ex) {
            return "Scaling metrics are currently unavailable.";
        }
    }

    public String fetchRecordSummary() {
        try {
            // Without an admin key Task 1 masks these fields, so nothing personal is repeated here.
            String json = get(removalBase);
            List<Map<String, Object>> records = mapper.readValue(json, new TypeReference<>() {});
            if (records.isEmpty()) {
                return "No records found in the redundancy system.";
            }
            StringBuilder sb = new StringBuilder("Records in system (" + records.size() + " total):\n");
            int limit = Math.min(records.size(), 5);
            for (int i = 0; i < limit; i++) {
                Map<String, Object> rec = records.get(i);
                sb.append("  ").append(rec.get("fullName"))
                    .append(" (").append(rec.get("email")).append(")")
                    .append(" - ").append(rec.get("status"));
                if (rec.get("validationReason") != null) {
                    sb.append(" [").append(rec.get("validationReason")).append("]");
                }
                sb.append("\n");
            }
            if (records.size() > limit) {
                sb.append("  ... and ").append(records.size() - limit).append(" more.");
            }
            return sb.toString();
        } catch (Exception ex) {
            return "I could not read the redundancy service status right now.";
        }
    }

    public String lookupBooking(String ticketNumber, String email) {
        try {
            HttpResponse<String> response = post(buspassBase + "/tickets/lookup",
                Map.of("ticketNumber", ticketNumber, "email", email));
            if (response.statusCode() == 404) {
                return "No booking matches that ticket number and email. Please check both and try again.";
            }
            if (response.statusCode() != 200) {
                throw new IOException("HTTP " + response.statusCode());
            }
            Map<String, Object> ticket = mapper.readValue(response.body(), new TypeReference<>() {});
            return "Booking " + ticket.get("status") + ": " + ticket.get("routeName")
                + " on " + ticket.get("travelDate") + ", seat " + ticket.get("seatNumber")
                + ". Passenger validation: " + describeValidation(String.valueOf(ticket.get("validationStatus")));
        } catch (Exception ex) {
            return "I could not reach the bus pass service to check that booking.";
        }
    }

    public String validateRecord(String fullName, String email, String phone) {
        try {
            HttpResponse<String> response = post(removalBase + "/validate",
                Map.of("fullName", fullName, "email", email, "phone", phone));
            Map<String, Object> result = mapper.readValue(response.body(), new TypeReference<>() {});
            if (response.statusCode() == 400) {
                return "That record is not valid: " + result.get("fieldErrors");
            }
            if (response.statusCode() != 200) {
                throw new IOException("HTTP " + response.statusCode());
            }
            return result.get("status") + ": " + result.get("validationReason") + ". Nothing was stored.";
        } catch (Exception ex) {
            return "I could not reach the data validation service right now.";
        }
    }

    private String describeValidation(String status) {
        return switch (status) {
            case "VERIFIED" -> "verified.";
            case "PENDING" -> "pending, because the data validation service could not be reached. It is retried automatically.";
            case "REVIEW" -> "held for manual review, because the details partly match another record.";
            default -> "not checked.";
        };
    }

    private String get(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(3))
            .GET()
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IOException("HTTP " + response.statusCode());
        }
        return response.body();
    }

    private HttpResponse<String> post(String url, Map<String, String> payload) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(3))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
            .build();

        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
