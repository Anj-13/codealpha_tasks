package com.codealpha.chatbot.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.codealpha.chatbot.model.Intent;

class IntentServiceTest {

    private final IntentService intents = new IntentService();

    @ParameterizedTest
    @CsvSource({
        "show routes, ROUTES",
        "where can i go, ROUTES",
        "book a ticket, BOOKING_HELP",
        "how do i book a ticket, BOOKING_HELP",
        "server scale status, SCALING",
        "show redundancy records, REDUNDANCY_STATUS",
        "hello, GREETING",
        "help, GREETING",
        "what can you do, GREETING",
        "purple monkey dishwasher, FALLBACK",
    })
    void keepsTheOriginalCommandsWorking(String message, Intent expected) {
        assertEquals(expected, intents.detect(message));
    }

    @ParameterizedTest
    @CsvSource({
        "which fare applies, PRICING",
        "this is the way, FALLBACK",
        "shipping details, FALLBACK",
    })
    void doesNotMatchKeywordsInsideOtherWords(String message, Intent expected) {
        assertEquals(expected, intents.detect(message));
    }

    @ParameterizedTest
    @CsvSource({
        "what are the fares, PRICING",
        "how much is a ticket, PRICING",
        "is there a student discount, PRICING",
        "can i get a refund, TICKET_RULES",
        "how do you verify a ticket, TICKET_RULES",
        "how does validation work, VALIDATION_HELP",
        "how do you detect duplicates, VALIDATION_HELP",
        "why was my record rejected, DUPLICATE_HELP",
        "what does false positive mean, DUPLICATE_HELP",
        "what is a 409, DUPLICATE_HELP",
        "my booking failed, BOOKING_ERROR_HELP",
        "why is my ticket pending, BOOKING_ERROR_HELP",
        "check my booking, BOOKING_LOOKUP",
        "ticket status, BOOKING_LOOKUP",
        "validate Ava ava@example.com 07123456789, VALIDATION_CHECK",
        "check a record, VALIDATION_CHECK",
    })
    void recognisesModuleSpecificQuestions(String message, Intent expected) {
        assertEquals(expected, intents.detect(message));
    }

    @ParameterizedTest
    @CsvSource({
        "123e4567-e89b-12d3-a456-426614174000",
        "my ticket is 123E4567-E89B-12D3-A456-426614174000 thanks",
    })
    void aTicketNumberAlwaysMeansALookup(String message) {
        assertEquals(Intent.BOOKING_LOOKUP, intents.detect(message));
    }

    @ParameterizedTest
    @CsvSource(value = {"NULL", "''"}, nullValues = "NULL")
    void emptyInputFallsBack(String message) {
        assertEquals(Intent.FALLBACK, intents.detect(message));
    }
}
