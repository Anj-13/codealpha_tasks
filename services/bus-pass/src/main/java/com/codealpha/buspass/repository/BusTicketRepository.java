package com.codealpha.buspass.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.codealpha.buspass.model.BusTicket;
import com.codealpha.buspass.model.ValidationStatus;

public interface BusTicketRepository extends JpaRepository<BusTicket, Long> {

    // Route is lazy and open-in-view is off, so it must be loaded with the ticket.
    @EntityGraph(attributePaths = "route")
    Optional<BusTicket> findByTicketNumber(String ticketNumber);

    boolean existsByBookingKey(String bookingKey);

    List<BusTicket> findTop50ByValidationStatusOrderByIdAsc(ValidationStatus validationStatus);

    @Query("select coalesce(max(t.seatNumber), 0) from BusTicket t where t.route.id = :routeId and t.travelDate = :travelDate")
    int findHighestSeat(@Param("routeId") Long routeId, @Param("travelDate") LocalDate travelDate);

    @Transactional
    @Modifying
    @Query("update BusTicket t set t.validationStatus = :status where t.id = :id")
    void updateValidationStatus(@Param("id") Long id, @Param("status") ValidationStatus status);
}
