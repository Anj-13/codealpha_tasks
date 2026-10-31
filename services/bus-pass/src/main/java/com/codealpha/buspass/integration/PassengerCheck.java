package com.codealpha.buspass.integration;

/** What the redundancy service said about a passenger. */
public enum PassengerCheck {
    /** Not seen before, so the passenger still needs storing. */
    UNIQUE,
    /** Already stored, or stored by this call. */
    KNOWN,
    /** Partly matches another record, or was refused, and needs manual review. */
    FLAGGED,
    /** No usable answer, so the check must be retried. */
    UNAVAILABLE,
    /** The integration is switched off. */
    DISABLED
}
