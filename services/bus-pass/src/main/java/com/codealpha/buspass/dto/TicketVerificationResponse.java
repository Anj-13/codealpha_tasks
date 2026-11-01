package com.codealpha.buspass.dto;

public record TicketVerificationResponse(String ticketNumber, boolean valid, String reason) {

    public static final String VALID = "VALID";
    public static final String TAMPERED = "TAMPERED";
    public static final String TOKEN_MISMATCH = "TOKEN_MISMATCH";
    public static final String NOT_ACTIVE = "NOT_ACTIVE";
    public static final String LEGACY_UNSIGNED = "LEGACY_UNSIGNED";

    public TicketVerificationResponse(String ticketNumber, String reason) {
        this(ticketNumber, VALID.equals(reason), reason);
    }
}
