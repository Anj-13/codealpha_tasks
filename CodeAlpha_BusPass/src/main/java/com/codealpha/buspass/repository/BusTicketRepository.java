package com.codealpha.buspass.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.codealpha.buspass.model.BusTicket;

public interface BusTicketRepository extends JpaRepository<BusTicket, Long> {
    Optional<BusTicket> findByTicketNumber(String ticketNumber);
}
