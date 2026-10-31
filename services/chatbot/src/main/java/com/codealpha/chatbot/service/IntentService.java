package com.codealpha.chatbot.service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import com.codealpha.chatbot.model.Intent;
import com.codealpha.chatbot.model.MessageDetails;

/**
 * Picks an intent from predefined patterns. The first matching rule wins, so specific
 * questions come before the broad keyword rules, and greetings come last.
 */
public class IntentService {

    private record Rule(Intent intent, Pattern pattern) {
        boolean matches(String text) {
            return pattern.matcher(text).find();
        }
    }

    private static final List<Rule> RULES = List.of(
        rule(Intent.BOOKING_LOOKUP, MessageDetails.TICKET_NUMBER.pattern()
            + "|\\b(booking|ticket) status\\b|\\bcheck (my |a )?(booking|ticket)\\b|\\bfind my (booking|ticket)\\b|\\bwhere is my (booking|ticket)\\b"),
        rule(Intent.VALIDATION_CHECK, "^\\s*(validate|check)\\s+(.*@|(a |this |my )?record\\b)"),
        rule(Intent.DUPLICATE_HELP, "\\b(409|422)\\b|false.positive|partial match|why\\b.*\\b(duplicate|redundant|rejected|flagged)\\b|duplicate.record error"),
        rule(Intent.BOOKING_ERROR_HELP, "\\b(book|booking|ticket)\\b.*\\b(error|errors|fail|failed|failing|problem|wrong)\\b"
            + "|route not found|already booked|already (have|has) a booking|\\bpending\\b"),
        rule(Intent.VALIDATION_HELP, "\\bhow\\b.*\\b(valid\\w*|redundan\\w*|dedup\\w*|duplicates?)\\b|validation (process|works?)|what is (data )?(validation|redundancy)"),
        rule(Intent.PRICING, "\\b(price|prices|pricing|fare|fares|cost|costs|discount|discounts|concession|concessions|how much)\\b"),
        rule(Intent.TICKET_RULES, "\\b(rules?|refunds?|cancel|cancellation|qr|verify|verification|seats?)\\b"),
        rule(Intent.ROUTES, "\\b(routes?|bus lines?|destinations?)\\b|where can i go"),
        rule(Intent.BOOKING_HELP, "\\b(book|booking|bookings|ticket|tickets|reserve)\\b"),
        rule(Intent.SCALING, "\\b(scale|scaling|traffic|servers?)\\b"),
        rule(Intent.REDUNDANCY_STATUS, "\\b(records?|redundancy|redundant|duplicates?)\\b"),
        rule(Intent.GREETING, "\\b(hello|hi|hey|help|commands|instructions)\\b|what can|how do i|what to type|what is this|who are you")
    );

    public Intent detect(String message) {
        String text = message == null ? "" : message.toLowerCase(Locale.ROOT);
        return RULES.stream()
            .filter(rule -> rule.matches(text))
            .map(Rule::intent)
            .findFirst()
            .orElse(Intent.FALLBACK);
    }

    private static Rule rule(Intent intent, String regex) {
        return new Rule(intent, Pattern.compile(regex));
    }
}
