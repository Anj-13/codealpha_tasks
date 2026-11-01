package com.codealpha.buspass.exception;

public class DuplicateBookingException extends RuntimeException {

    public DuplicateBookingException() {
        super("This passenger already has a booking on this route and date");
    }
}
