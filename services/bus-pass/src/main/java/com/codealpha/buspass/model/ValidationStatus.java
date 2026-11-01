package com.codealpha.buspass.model;

/** How far the redundancy service has checked a ticket's passenger. */
public enum ValidationStatus {
    /** Issued while the check could not finish. It is retried in the background. */
    PENDING,
    /** The passenger is stored in the redundancy service. */
    VERIFIED,
    /** The passenger partly matches another record and needs manual review. */
    REVIEW,
    /** The integration was off, or the ticket predates validation. */
    NOT_CHECKED
}
