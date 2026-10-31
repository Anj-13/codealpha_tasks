package com.codealpha.chatbot.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ExternalKnowledgeService {

    private final HttpClient client;
    private final ObjectMapper mapper;
    private final String buspassBase;
    private final String removalBase;

    public ExternalKnowledgeService(ObjectMapper mapper) {
        this.mapper = mapper;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        this.buspassBase = System.getenv().getOrDefault("BUSPASS_BASE_URL", "http://localhost:8082/api/buspass");
        this.removalBase = System.getenv().getOrDefault("REMOVAL_BASE_URL", "http://localhost:8081/api/records");
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
}
