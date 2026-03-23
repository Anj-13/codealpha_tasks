package com.codealpha.buspass.model;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "routes")
public class Route {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 120)
    private String source;

    @Column(nullable = false, length = 120)
    private String destination;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal baseFare;

    protected Route() {
    }

    public Route(String code, String source, String destination, BigDecimal baseFare) {
        this.code = code;
        this.source = source;
        this.destination = destination;
        this.baseFare = baseFare;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getSource() {
        return source;
    }

    public String getDestination() {
        return destination;
    }

    public BigDecimal getBaseFare() {
        return baseFare;
    }
}
