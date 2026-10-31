package com.codealpha.buspass.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.codealpha.buspass.model.BusTicket;

public interface BusTicketRepository extends JpaRepository<BusTicket, Long> {

    // Route is lazy and open-in-view is off, so it must be loaded with the ticket.
    @EntityGraph(attributePaths = "route")
    Optional<BusTicket> findByTicketNumber(String ticketNumber);
}
