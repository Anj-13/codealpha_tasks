package com.codealpha.buspass;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.codealpha.buspass.model.Route;
import com.codealpha.buspass.repository.RouteRepository;

@Component
public class DataSeeder implements CommandLineRunner {

    private final RouteRepository routeRepository;

    public DataSeeder(RouteRepository routeRepository) {
        this.routeRepository = routeRepository;
    }

    @Override
    public void run(String... args) {
        if (routeRepository.count() > 0) {
            return;
        }

        List<Route> routes = List.of(
            new Route("R-100", "Leeds", "Manchester", new BigDecimal("12.50")),
            new Route("R-110", "Leeds", "York", new BigDecimal("8.00")),
            new Route("R-120", "Leeds", "London", new BigDecimal("34.99")),
            new Route("R-130", "Sheffield", "Leeds", new BigDecimal("7.50"))
        );

        routeRepository.saveAll(routes);
    }
}
