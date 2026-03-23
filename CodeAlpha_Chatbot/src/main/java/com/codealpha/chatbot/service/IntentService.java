package com.codealpha.chatbot.service;

import java.util.Locale;

public class IntentService {

    public String detect(String message) {
        String text = message == null ? "" : message.toLowerCase(Locale.ROOT);

        if (text.contains("hello") || text.contains("hi") || text.contains("hey")) {
            return "GREETING";
        }
        if (text.contains("route") || text.contains("bus line") || text.contains("where can i go")) {
            return "ROUTES";
        }
        if (text.contains("book") || text.contains("ticket") || text.contains("price") || text.contains("fare")) {
            return "BOOKING_HELP";
        }
        if (text.contains("scale") || text.contains("traffic") || text.contains("server")) {
            return "SCALING";
        }
        if (text.contains("record") || text.contains("duplicate") || text.contains("redundancy")) {
            return "REDUNDANCY_STATUS";
        }

        return "FALLBACK";
    }
}
