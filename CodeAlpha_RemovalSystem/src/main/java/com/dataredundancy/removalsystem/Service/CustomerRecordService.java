package com.dataredundancy.removalsystem.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dataredundancy.removalsystem.dto.RecordRequest;
import com.dataredundancy.removalsystem.dto.RecordResponse;
import com.dataredundancy.removalsystem.model.CustomerRecord;
import com.dataredundancy.removalsystem.model.RecordStatus;
import com.dataredundancy.removalsystem.repository.CustomerRecordRepository;

@Service
public class CustomerRecordService {

    private final CustomerRecordRepository repository;

    public CustomerRecordService(CustomerRecordRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public RecordResponse createRecord(RecordRequest request) {
        String normalizedName = normalizeText(request.getFullName());
        String normalizedEmail = normalizeEmail(request.getEmail());
        String normalizedPhone = normalizePhone(request.getPhone());
        String normalizedAddress = normalizeText(request.getAddress());

        String fingerprint = createFingerprint(normalizedName, normalizedEmail, normalizedPhone, normalizedAddress);

        RecordStatus status;
        String reason;

        if (repository.existsByFingerPrintHash(fingerprint)) {
            status = RecordStatus.REDUNDANT;
            reason = "Exact duplicate detected from fingerprint";
        } else if (repository.existsByEmailIgnoreCaseAndPhone(normalizedEmail, normalizedPhone)) {
            status = RecordStatus.REDUNDANT;
            reason = "Duplicate detected from matching email and phone";
        } else if (repository.existsByEmailIgnoreCase(normalizedEmail) || repository.existsByPhone(normalizedPhone)) {
            status = RecordStatus.FALSE_POSITIVE;
            reason = "Partial match found and flagged for manual review";
        } else {
            status = RecordStatus.UNIQUE;
            reason = "Record validated and accepted";
        }

        if (status != RecordStatus.UNIQUE) {
            return buildNonStoredResponse(normalizedName, normalizedEmail, normalizedPhone, normalizedAddress, status, reason);
        }

        CustomerRecord record = new CustomerRecord();
        record.setFullname(normalizedName);
        record.setEmail(normalizedEmail);
        record.setPhone(normalizedPhone);
        record.setAddress(normalizedAddress);
        record.setFingerprintHash(fingerprint);
        record.setStatus(status);
        record.setValidationReason(reason);

        CustomerRecord saved = repository.save(record);
        return toResponse(saved, true);
    }

    @Transactional(readOnly = true)
    public List<RecordResponse> getAllRecords() {
        return repository.findAll().stream().map(record -> toResponse(record, true)).toList();
    }

    @Transactional(readOnly = true)
    public RecordResponse getRecordById(Long id) {
        CustomerRecord record = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Record not found with id: " + id));
        return toResponse(record, true);
    }

    private RecordResponse buildNonStoredResponse(String name, String email, String phone, String address,
            RecordStatus status, String reason) {
        RecordResponse response = new RecordResponse();
        response.setFullName(name);
        response.setEmail(email);
        response.setPhone(phone);
        response.setAddress(address);
        response.setStatus(status);
        response.setValidationReason(reason);
        response.setStored(false);
        response.setCreatedAt(LocalDateTime.now());
        return response;
    }

    private RecordResponse toResponse(CustomerRecord record, boolean stored) {
        RecordResponse response = new RecordResponse();
        response.setId(record.getId());
        response.setFullName(record.getFullname());
        response.setEmail(record.getEmail());
        response.setPhone(record.getPhone());
        response.setAddress(record.getAddress());
        response.setStatus(record.getStatus());
        response.setValidationReason(record.getValidationReason());
        response.setStored(stored);
        response.setCreatedAt(record.getCreatedAt());
        return response;
    }

    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    private String normalizeEmail(String email) {
        return normalizeText(email).toLowerCase();
    }

    private String normalizePhone(String phone) {
        return normalizeText(phone).replaceAll("[^0-9]", "");
    }

    private String createFingerprint(String name, String email, String phone, String address) {
        String payload = String.join("|", name.toLowerCase(), email, phone, address.toLowerCase());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte value : hash) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is not available", ex);
        }
    }
}
