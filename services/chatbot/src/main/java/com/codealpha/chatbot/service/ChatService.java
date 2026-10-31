package com.codealpha.chatbot.service;

import com.codealpha.chatbot.model.AnswerSource;
import com.codealpha.chatbot.model.ChatResponse;
import com.codealpha.chatbot.model.Intent;
import com.codealpha.chatbot.model.MessageDetails;

public class ChatService {

    private final IntentService intentService;
    private final ExternalKnowledgeService externalKnowledgeService;

    public ChatService(IntentService intentService, ExternalKnowledgeService externalKnowledgeService) {
        this.intentService = intentService;
        this.externalKnowledgeService = externalKnowledgeService;
    }

    public ChatResponse answer(String message) {
        Intent intent = intentService.detect(message);

        if (StaticResponses.covers(intent)) {
            return new ChatResponse(intent, StaticResponses.forIntent(intent), AnswerSource.STATIC);
        }

        MessageDetails details = MessageDetails.from(message);
        return switch (intent) {
            case ROUTES -> live(intent, externalKnowledgeService.fetchRoutesSummary());
            case PRICING -> live(intent, StaticResponses.FARE_RULES + "\n\n" + externalKnowledgeService.fetchFaresSummary());
            case SCALING -> live(intent, externalKnowledgeService.fetchScaleSummary());
            case REDUNDANCY_STATUS -> live(intent, externalKnowledgeService.fetchRecordSummary());
            case BOOKING_LOOKUP -> details.ticketNumber() == null || details.email() == null
                ? new ChatResponse(intent, StaticResponses.LOOKUP_NEEDS_DETAILS, AnswerSource.STATIC)
                : live(intent, externalKnowledgeService.lookupBooking(details.ticketNumber(), details.email()));
            case VALIDATION_CHECK -> details.name() == null || details.email() == null || details.phone() == null
                ? new ChatResponse(intent, StaticResponses.CHECK_NEEDS_DETAILS, AnswerSource.STATIC)
                : live(intent, externalKnowledgeService.validateRecord(details.name(), details.email(), details.phone()));
            default -> throw new IllegalStateException("No answer defined for " + intent);
        };
    }

    private ChatResponse live(Intent intent, String response) {
        return new ChatResponse(intent, response, AnswerSource.LIVE);
    }
}
