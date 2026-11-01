package com.codealpha.buspass;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.codealpha.buspass.model.PassengerCategory;
import com.codealpha.buspass.model.Route;
import com.codealpha.buspass.service.PricingService;
import com.codealpha.buspass.service.TicketSigningKey;

class PricingServiceTests {

    private static final Route ROUTE = new Route("R-120", "Leeds", "London", new BigDecimal("34.99"));
    private static final LocalDate DATE = LocalDate.of(2030, 1, 15);

    private static PricingService pricing(String secret) throws IOException {
        return new PricingService(new TicketSigningKey(secret, "unused"));
    }

    @Test
    void appliesCategoryDiscountsAndRoundsToPence() throws IOException {
        PricingService pricing = pricing("secret");

        assertThat(pricing.calculateFare(ROUTE, PassengerCategory.ADULT)).isEqualByComparingTo("34.99");
        assertThat(pricing.calculateFare(ROUTE, PassengerCategory.STUDENT)).isEqualByComparingTo("27.99");
        assertThat(pricing.calculateFare(ROUTE, PassengerCategory.SENIOR)).isEqualByComparingTo("24.49");
    }

    @Test
    void signaturesDependOnTheSecret() throws IOException {
        BigDecimal fare = new BigDecimal("34.99");

        assertThat(pricing("one").checksum("ticket", "R-120", fare, "GBP"))
            .isEqualTo(pricing("one").checksum("ticket", "R-120", fare, "GBP"))
            .isNotEqualTo(pricing("two").checksum("ticket", "R-120", fare, "GBP"))
            .hasSize(64);
    }

    @Test
    void bookingKeyIgnoresCaseAndSpacing() throws IOException {
        PricingService pricing = pricing("secret");

        assertThat(pricing.bookingKey("a@example.com", "Pax One", "R-100", DATE))
            .isEqualTo(pricing.bookingKey(" A@EXAMPLE.COM ", "  pax   ONE ", "r-100", DATE));
    }

    @Test
    void bookingKeyChangesWithAnyPartOfTheBooking() throws IOException {
        PricingService pricing = pricing("secret");
        String key = pricing.bookingKey("a@example.com", "Pax One", "R-100", DATE);

        assertThat(key)
            .isNotEqualTo(pricing.bookingKey("b@example.com", "Pax One", "R-100", DATE))
            .isNotEqualTo(pricing.bookingKey("a@example.com", "Pax Two", "R-100", DATE))
            .isNotEqualTo(pricing.bookingKey("a@example.com", "Pax One", "R-110", DATE))
            .isNotEqualTo(pricing.bookingKey("a@example.com", "Pax One", "R-100", DATE.plusDays(1)));
    }
}
