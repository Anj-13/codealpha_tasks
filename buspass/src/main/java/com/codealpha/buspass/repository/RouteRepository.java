package com.codealpha.buspass.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.codealpha.buspass.model.Route;

public interface RouteRepository extends JpaRepository<Route, Long> {
    Optional<Route> findByCodeIgnoreCase(String code);
}
