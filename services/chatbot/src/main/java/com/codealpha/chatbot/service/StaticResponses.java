package com.codealpha.chatbot.service;

import java.util.Map;

import com.codealpha.chatbot.model.Intent;

/** Fixed answers. Anything that needs current data belongs in {@link ExternalKnowledgeService}. */
public final class StaticResponses {

    public static final String FARE_RULES = """
        Fares depend on the route and passenger category:
        • Adult: full fare
        • Student: 80% of the fare
        • Senior: 70% of the fare""";

    public static final String LOOKUP_NEEDS_DETAILS = """
        To check a booking I need both your ticket number and the email used to book, for example:
        check booking 123e4567-e89b-12d3-a456-426614174000 you@example.com""";

    public static final String CHECK_NEEDS_DETAILS = """
        To check a record I need a name, an email and a phone number, for example:
        validate Ava Thompson ava@example.com 07123456789
        Nothing is stored by this check.""";

    private static final Map<Intent, String> RESPONSES = Map.of(
        Intent.GREETING, """
            Hello! Here is what you can ask me:

            Bus pass
            • "show routes" — list all bus routes
            • "what are the fares" — fares and discounts
            • "book a ticket" — how to book
            • "check booking <ticket number> <email>" — booking status

            Data validation
            • "how does validation work" — the checks explained
            • "validate <name> <email> <phone>" — check a record
            • "show redundancy records" — masked records in the system

            Platform
            • "server scale status" — scaling metrics
            • "help" or "commands" — show this message again""",
        Intent.BOOKING_HELP, """
            To book a ticket, give a passenger name, email, phone number, route code, travel date and passenger category (ADULT, STUDENT or SENIOR).
            Use the Bus Pass page, or POST the same details to /api/buspass/tickets/book.
            Ask "show routes" to see the route codes.""",
        Intent.TICKET_RULES, """
            Ticket rules:
            • Each ticket has a unique number and an assigned seat.
            • Each passenger can hold one booking per route and date.
            • Tickets are signed, so any later change to the fare, email or date fails verification.
            • Viewing a booking needs both the ticket number and the booking email.
            • Online cancellation and refunds are not available yet.""",
        Intent.BOOKING_ERROR_HELP, """
            Common booking outcomes:
            • 400: a field is missing or invalid, or the travel date is in the past.
            • 404: the route code does not exist. Ask "show routes" for valid codes.
            • 409: this passenger already has a booking on that route and date.
            • Validation PENDING: the ticket is valid, but the data validation service could not be reached. The check is retried automatically.
            • Validation REVIEW: the passenger partly matches an existing record and needs a manual check. The ticket is still issued.""",
        Intent.VALIDATION_HELP, """
            How data validation works:
            1. Names, emails and phone numbers are normalised, so spacing and capitals do not hide a match.
            2. A SHA-256 fingerprint of the record is compared with stored records.
            3. The email and phone are then checked separately.
            A new record is UNIQUE and stored. A full match is REDUNDANT and not stored. A match on the email or phone alone is FALSE_POSITIVE and held for manual review.""",
        Intent.DUPLICATE_HELP, """
            What the duplicate outcomes mean:
            • REDUNDANT (409): the same record, or the same email and phone, is already stored, so nothing new was saved.
            • FALSE_POSITIVE (422): the email or the phone matches a different record. It may be a new person or a typo, so it is not stored and needs manual review.
            To fix a FALSE_POSITIVE, check the email and phone number, then submit again.""",
        Intent.FALLBACK, """
            I didn't understand that. Try one of these:
            • "show routes"
            • "what are the fares"
            • "how does validation work"
            • "check booking <ticket number> <email>"
            Or type "help" to see all options."""
    );

    private StaticResponses() {
    }

    public static boolean covers(Intent intent) {
        return RESPONSES.containsKey(intent);
    }

    public static String forIntent(Intent intent) {
        return RESPONSES.get(intent);
    }
}
