package com.codealpha.chatbot.service;

import com.codealpha.chatbot.model.ChatResponse;

public class ChatService {

    private final IntentService intentService;
    private final ExternalKnowledgeService externalKnowledgeService;

    public ChatService(IntentService intentService, ExternalKnowledgeService externalKnowledgeService) {
        this.intentService = intentService;
        this.externalKnowledgeService = externalKnowledgeService;
    }

    public ChatResponse answer(String message) {
        String intent = intentService.detect(message);
        String response = switch (intent) {
            case "GREETING" -> "Hello! I can help with routes, ticket booking guidance, scaling status, and redundancy records.";
            case "ROUTES" -> externalKnowledgeService.fetchRoutesSummary();
            case "BOOKING_HELP" -> "Book via POST /api/buspass/tickets/book with passengerName, email, routeCode, travelDate, and passengerCategory.";
            case "SCALING" -> externalKnowledgeService.fetchScaleSummary();
            case "REDUNDANCY_STATUS" -> externalKnowledgeService.fetchRecordSummary();
            default -> "Try asking about routes, booking, server scaling, or redundancy records.";
        };
        return new ChatResponse(intent, response);
    }
}
