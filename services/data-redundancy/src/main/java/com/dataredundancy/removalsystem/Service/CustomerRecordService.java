package com.dataredundancy.removalsystem.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dataredundancy.removalsystem.dto.RecordRequest;
import com.dataredundancy.removalsystem.dto.RecordResponse;
import com.dataredundancy.removalsystem.dto.RecordSummaryResponse;
import com.dataredundancy.removalsystem.exception.RecordNotFoundException;
import com.dataredundancy.removalsystem.model.CustomerRecord;
import com.dataredundancy.removalsystem.model.RecordStatus;
import com.dataredundancy.removalsystem.repository.CustomerRecordRepository;

@Service
public class CustomerRecordService {

    private record NormalizedRecord(String name, String email, String phone, String address, String fingerprint) {}

    private final CustomerRecordRepository repository;

    public CustomerRecordService(CustomerRecordRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public RecordResponse createRecord(RecordRequest request) {
        NormalizedRecord normalized = normalize(request);
        RecordResponse outcome = classify(normalized);

        if (outcome.getStatus() != RecordStatus.UNIQUE) {
            return outcome;
        }

        CustomerRecord record = new CustomerRecord();
        record.setFullname(normalized.name());
        record.setEmail(normalized.email());
        record.setPhone(normalized.phone());
        record.setAddress(normalized.address());
        record.setFingerprintHash(normalized.fingerprint());
        record.setStatus(outcome.getStatus());
        record.setValidationReason(outcome.getValidationReason());

        CustomerRecord saved = repository.save(record);
        return toResponse(saved, true);
    }

    /** Runs the same checks as {@link #createRecord} without storing anything. */
    @Transactional(readOnly = true)
    public RecordResponse validateRecord(RecordRequest request) {
        RecordResponse outcome = classify(normalize(request));
        if (outcome.getStatus() == RecordStatus.UNIQUE) {
            outcome.setValidationReason("No matching record found");
        }
        return outcome;
    }

    @Transactional(readOnly = true)
    public List<RecordResponse> getAllRecords(boolean fullAccess) {
        return repository.findAll().stream()
                .map(record -> visibleTo(toResponse(record, true), fullAccess))
                .toList();
    }

    @Transactional(readOnly = true)
    public RecordResponse getRecordById(Long id, boolean fullAccess) {
        CustomerRecord record = repository.findById(id)
                .orElseThrow(() -> new RecordNotFoundException(id));
        return visibleTo(toResponse(record, true), fullAccess);
    }

    @Transactional(readOnly = true)
    public RecordSummaryResponse getSummary() {
        LocalDateTime lastStoredAt = repository.findTopByOrderByCreatedAtDesc()
                .map(CustomerRecord::getCreatedAt)
                .orElse(null);
        return new RecordSummaryResponse(repository.count(), lastStoredAt);
    }

    private NormalizedRecord normalize(RecordRequest request) {
        String name = normalizeText(request.getFullName());
        String email = normalizeEmail(request.getEmail());
        String phone = normalizePhone(request.getPhone());
        String address = normalizeText(request.getAddress());
        return new NormalizedRecord(name, email, phone, address, createFingerprint(name, email, phone, address));
    }

    private RecordResponse classify(NormalizedRecord record) {
        RecordStatus status;
        String reason;

        if (repository.existsByFingerPrintHash(record.fingerprint())) {
            status = RecordStatus.REDUNDANT;
            reason = "Exact duplicate detected from fingerprint";
        } else if (repository.existsByEmailIgnoreCaseAndPhone(record.email(), record.phone())) {
            status = RecordStatus.REDUNDANT;
            reason = "Duplicate detected from matching email and phone";
        } else if (repository.existsByEmailIgnoreCase(record.email()) || repository.existsByPhone(record.phone())) {
            status = RecordStatus.FALSE_POSITIVE;
            reason = "Partial match found and flagged for manual review";
        } else {
            status = RecordStatus.UNIQUE;
            reason = "Record validated and accepted";
        }

        return buildNonStoredResponse(record.name(), record.email(), record.phone(), record.address(), status, reason);
    }

    private RecordResponse visibleTo(RecordResponse response, boolean fullAccess) {
        if (fullAccess) {
            return response;
        }
        response.setFullName(maskName(response.getFullName()));
        response.setEmail(maskEmail(response.getEmail()));
        response.setPhone(maskPhone(response.getPhone()));
        response.setAddress(null);
        response.setMasked(true);
        return response;
    }

    private String maskName(String name) {
        return Arrays.stream(name.split(" "))
                .map(part -> part.isEmpty() ? part : part.charAt(0) + "***")
                .collect(Collectors.joining(" "));
    }

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        return at < 1 ? "***" : email.charAt(0) + "***" + email.substring(at);
    }

    private String maskPhone(String phone) {
        int visible = Math.min(3, phone.length());
        return "*".repeat(phone.length() - visible) + phone.substring(phone.length() - visible);
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
