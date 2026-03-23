package com.dataredundancy.removalsystem.Controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dataredundancy.removalsystem.dto.RecordRequest;
import com.dataredundancy.removalsystem.dto.RecordResponse;
import com.dataredundancy.removalsystem.model.RecordStatus;
import com.dataredundancy.removalsystem.Service.CustomerRecordService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/records")
@Validated
public class CustomerRecordController {

    private final CustomerRecordService service;

    public CustomerRecordController(CustomerRecordService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<RecordResponse> createRecord(@Valid @RequestBody RecordRequest request) {
        RecordResponse response = service.createRecord(request);

        if (response.isStored()) {
            URI location = URI.create("/api/records/" + response.getId());
            return ResponseEntity.created(location).body(response);
        }

        if (response.getStatus() == RecordStatus.REDUNDANT) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(response);
    }

    @GetMapping
    public ResponseEntity<List<RecordResponse>> getAllRecords() {
        return ResponseEntity.ok(service.getAllRecords());
    }

    @GetMapping("/{id}")
    public ResponseEntity<RecordResponse> getRecordById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getRecordById(id));
    }
}
