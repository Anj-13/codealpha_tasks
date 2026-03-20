package com.codealpha.buspass.dto;

import java.math.BigDecimal;

public class RouteResponse {

    private String code;
    private String source;
    private String destination;
    private BigDecimal baseFare;

    public RouteResponse(String code, String source, String destination, BigDecimal baseFare) {
        this.code = code;
        this.source = source;
        this.destination = destination;
        this.baseFare = baseFare;
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
