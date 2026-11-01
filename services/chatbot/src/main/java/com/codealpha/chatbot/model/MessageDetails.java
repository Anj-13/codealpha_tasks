package com.codealpha.chatbot.model;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Values a live lookup needs, pulled out of a chat message. Anything not found is null. */
public record MessageDetails(String ticketNumber, String email, String phone, String name) {

    public static final Pattern TICKET_NUMBER = Pattern.compile(
        "\\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern EMAIL = Pattern.compile("[^\\s@,;]+@[^\\s@,;]+\\.[^\\s@,;]+");
    private static final Pattern PHONE = Pattern.compile("\\+?\\d[\\d ()-]{5,18}\\d");
    private static final Pattern COMMAND_WORDS = Pattern.compile(
        "^\\s*(validate|check)\\b(\\s+(a|this|my)\\b)?(\\s+record\\b)?", Pattern.CASE_INSENSITIVE);

    public static MessageDetails from(String message) {
        String remaining = TICKET_NUMBER.matcher(message).replaceAll(" ");

        Matcher ticket = TICKET_NUMBER.matcher(message);
        String ticketNumber = ticket.find() ? ticket.group().toLowerCase(Locale.ROOT) : null;

        Matcher emailMatch = EMAIL.matcher(remaining);
        String email = emailMatch.find() ? emailMatch.group() : null;
        remaining = EMAIL.matcher(remaining).replaceAll(" ");

        Matcher phoneMatch = PHONE.matcher(remaining);
        String phone = phoneMatch.find() ? phoneMatch.group().trim() : null;
        remaining = PHONE.matcher(remaining).replaceAll(" ");

        String name = COMMAND_WORDS.matcher(remaining).replaceFirst(" ").replaceAll("[^\\p{L} '-]", " ")
            .trim().replaceAll("\\s+", " ");

        return new MessageDetails(ticketNumber, email, phone, name.isEmpty() ? null : name);
    }
}
