package com.codealpha.buspass.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.codealpha.buspass.model.Route;

import jakarta.persistence.LockModeType;

public interface RouteRepository extends JpaRepository<Route, Long> {
    Optional<Route> findByCodeIgnoreCase(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Route r where r.id = :id")
    Optional<Route> findByIdForUpdate(@Param("id") Long id);
}
