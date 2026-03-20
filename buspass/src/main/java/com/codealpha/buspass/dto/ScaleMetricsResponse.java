package com.codealpha.buspass.dto;

public class ScaleMetricsResponse {

    private int totalBookings;
    private int recommendedServers;
    private int maxServers;

    public ScaleMetricsResponse(int totalBookings, int recommendedServers, int maxServers) {
        this.totalBookings = totalBookings;
        this.recommendedServers = recommendedServers;
        this.maxServers = maxServers;
    }

    public int getTotalBookings() {
        return totalBookings;
    }

    public int getRecommendedServers() {
        return recommendedServers;
    }

    public int getMaxServers() {
        return maxServers;
    }
}
