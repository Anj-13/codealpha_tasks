package com.dataredundancy.removalsystem.exception;

public class AdminKeyRejectedException extends RuntimeException {

    public AdminKeyRejectedException() {
        super("Admin key is invalid or admin access is not configured");
    }
}
