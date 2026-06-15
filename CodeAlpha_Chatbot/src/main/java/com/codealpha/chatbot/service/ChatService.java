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
            case "GREETING" -> """
                Hello! Here is what you can ask me:
                \u2022 "show routes" \u2014 list all bus routes
                \u2022 "book a ticket" \u2014 how to book
                \u2022 "server scale status" \u2014 scaling metrics
                \u2022 "show redundancy records" \u2014 records in the system
                \u2022 "help" or "commands" \u2014 show this message again""";
            case "ROUTES" -> externalKnowledgeService.fetchRoutesSummary();
            case "BOOKING_HELP" -> "Book via POST /api/buspass/tickets/book with passengerName, email, routeCode, travelDate, and passengerCategory.";
            case "SCALING" -> externalKnowledgeService.fetchScaleSummary();
            case "REDUNDANCY_STATUS" -> externalKnowledgeService.fetchRecordSummary();
            default -> """
                I didn't understand that. Try one of these:
                \u2022 "show routes"
                \u2022 "book a ticket"
                \u2022 "server scale status"
                \u2022 "show redundancy records"
                Or type "help" to see all options.""";
        };
        return new ChatResponse(intent, response);
    }
}
