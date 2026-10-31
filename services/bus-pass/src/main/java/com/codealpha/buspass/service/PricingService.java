package com.codealpha.buspass.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;

import com.codealpha.buspass.model.PassengerCategory;
import com.codealpha.buspass.model.Route;

@Service
public class PricingService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final SecretKeySpec signingKey;

    public PricingService(TicketSigningKey signingKey) {
        this.signingKey = new SecretKeySpec(signingKey.bytes(), HMAC_ALGORITHM);
    }

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
        return sign(ticketNumber + "|" + routeCode + "|" + fare + "|" + currency);
    }

    public String token(String ticketNumber, String email, String travelDate) {
        return sign(ticketNumber + "|" + email.toLowerCase() + "|" + travelDate);
    }

    /** Identifies one passenger on one route and date, ignoring case and extra spaces. */
    public String bookingKey(String email, String passengerName, String routeCode, LocalDate travelDate) {
        String name = passengerName.trim().replaceAll("\\s+", " ");
        String raw = String.join("|", email.trim(), name, routeCode, travelDate.toString()).toLowerCase();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to build hash", e);
        }
    }

    public boolean matches(String expected, String actual) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }

    private String sign(String raw) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(signingKey);
            return HexFormat.of().formatHex(mac.doFinal(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to sign ticket", e);
        }
    }
}
