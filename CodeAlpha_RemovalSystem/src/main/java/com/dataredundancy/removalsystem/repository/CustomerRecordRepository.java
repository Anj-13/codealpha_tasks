package com.dataredundancy.removalsystem.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.dataredundancy.removalsystem.model.CustomerRecord;

@Repository
public interface CustomerRecordRepository extends JpaRepository<CustomerRecord, Long> {

	Optional<CustomerRecord> findByFingerPrintHash(String fingerPrintHash);

	boolean existsByFingerPrintHash(String fingerPrintHash);

	boolean existsByEmailIgnoreCase(String email);

	boolean existsByPhone(String phone);

	boolean existsByEmailIgnoreCaseAndPhone(String email, String phone);
}