package com.codealpha.buspass.service;

import org.springframework.stereotype.Service;

import com.codealpha.buspass.dto.ScaleMetricsResponse;

@Service
public class ScaleService {

    private static final int MAX_SERVERS = 20;

    public ScaleMetricsResponse calculate(int totalBookings) {
        int recommended = Math.max(1, Math.min(MAX_SERVERS, 1 + (totalBookings / 25)));
        return new ScaleMetricsResponse(totalBookings, recommended, MAX_SERVERS);
    }
}
