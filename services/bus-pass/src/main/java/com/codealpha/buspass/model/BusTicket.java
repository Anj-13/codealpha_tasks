package com.codealpha.buspass.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "bus_tickets")
public class BusTicket {

    private static final int CURRENT_SIGNATURE_VERSION = 1;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 36)
    private String ticketNumber;

    @Column(nullable = false, unique = true, length = 64)
    private String qrToken;

    @Column(nullable = false, length = 120)
    private String passengerName;

    @Column(nullable = false, length = 150)
    private String email;

    // The next three are null on tickets issued before these columns existed.
    @Column(length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 20)
    private ValidationStatus validationStatus;

    @Column(length = 64, unique = true)
    private String bookingKey;

    /** Null marks a ticket hashed without a secret, which can no longer be verified. */
    private Integer signatureVersion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "route_id", nullable = false)
    private Route route;

    @Column(nullable = false)
    private LocalDate travelDate;

    @Column(nullable = false)
    private int seatNumber;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal farePaid;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 64)
    private String pricingChecksum;

    // Stored as text: H2 ENUM columns returned ordinals instead of labels after a restart and a write.
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private PassengerCategory passengerCategory;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private BookingStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected BusTicket() {
    }

    public BusTicket(String ticketNumber, String qrToken, String passengerName, String email, String phone, Route route,
                     LocalDate travelDate, int seatNumber, BigDecimal farePaid, String currency,
                     String pricingChecksum, PassengerCategory passengerCategory, BookingStatus status,
                     ValidationStatus validationStatus, String bookingKey) {
        this.ticketNumber = ticketNumber;
        this.qrToken = qrToken;
        this.passengerName = passengerName;
        this.email = email;
        this.phone = phone;
        this.route = route;
        this.travelDate = travelDate;
        this.seatNumber = seatNumber;
        this.farePaid = farePaid;
        this.currency = currency;
        this.pricingChecksum = pricingChecksum;
        this.passengerCategory = passengerCategory;
        this.status = status;
        this.validationStatus = validationStatus;
        this.bookingKey = bookingKey;
        this.signatureVersion = CURRENT_SIGNATURE_VERSION;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getTicketNumber() {
        return ticketNumber;
    }

    public String getQrToken() {
        return qrToken;
    }

    public String getPassengerName() {
        return passengerName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public ValidationStatus getValidationStatus() {
        return validationStatus;
    }

    public void setValidationStatus(ValidationStatus validationStatus) {
        this.validationStatus = validationStatus;
    }

    public Integer getSignatureVersion() {
        return signatureVersion;
    }

    public Route getRoute() {
        return route;
    }

    public LocalDate getTravelDate() {
        return travelDate;
    }

    public int getSeatNumber() {
        return seatNumber;
    }

    public BigDecimal getFarePaid() {
        return farePaid;
    }

    public String getCurrency() {
        return currency;
    }

    public String getPricingChecksum() {
        return pricingChecksum;
    }

    public PassengerCategory getPassengerCategory() {
        return passengerCategory;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
