package com.codealpha.buspass.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.springframework.stereotype.Service;

import com.codealpha.buspass.model.PassengerCategory;
import com.codealpha.buspass.model.Route;

@Service
public class PricingService {

    public BigDecimal calculateFare(Route route, PassengerCategory category) {
        BigDecimal base = route.getBaseFare();

        BigDecimal multiplier = switch (category) {
            case STUDENT -> new BigDecimal("0.80");
            case SENIOR -> new BigDecimal("0.70");
            case ADULT -> BigDecimal.ONE;
        };

        return base.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
    }

    public String checksum(String ticketNumber, String routeCode, BigDecimal fare, String currency) {
        return sha256(ticketNumber + "|" + routeCode + "|" + fare + "|" + currency);
    }

    public String token(String ticketNumber, String email, String travelDate) {
        return sha256(ticketNumber + "|" + email.toLowerCase() + "|" + travelDate);
    }

    private String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Unable to build hash", e);
        }
    }
}
