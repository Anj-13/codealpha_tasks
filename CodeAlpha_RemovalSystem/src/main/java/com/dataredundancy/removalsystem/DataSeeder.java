package com.dataredundancy.removalsystem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.dataredundancy.removalsystem.Service.CustomerRecordService;
import com.dataredundancy.removalsystem.dto.RecordRequest;
import com.dataredundancy.removalsystem.repository.CustomerRecordRepository;

@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataSeeder.class);

    private final CustomerRecordService service;
    private final CustomerRecordRepository repository;

    @Value("${app.seed.enabled:true}")
    private boolean seedEnabled;

    public DataSeeder(CustomerRecordService service, CustomerRecordRepository repository) {
        this.service = service;
        this.repository = repository;
    }

    @Override
    public void run(String... args) {
        if (!seedEnabled) {
            logger.info("Seed data is disabled (app.seed.enabled=false)");
            return;
        }

        if (repository.count() > 0) {
            logger.info("Seed skipped because records already exist");
            return;
        }

        service.createRecord(request("Ava Thompson", "ava@example.com", "07123 456 789", "21 King Street"));
        service.createRecord(request("Noah Patel", "noah@example.com", "07888 111 222", "44 River Road"));
        service.createRecord(request("Lily Smith", "lily@example.com", "07999 333 444", "8 Garden Avenue"));

        // Intentional duplicate to demonstrate redundancy detection (not stored).
        service.createRecord(request("Ava   Thompson", "AVA@example.com", "07123456789", "21 King Street"));

        // Intentional partial match to demonstrate false-positive classification (not stored).
        service.createRecord(request("Ava Thompson", "ava@example.com", "07000 000 000", "99 New Lane"));

        logger.info("Seed complete. Stored unique records: {}", repository.count());
    }

    private RecordRequest request(String fullName, String email, String phone, String address) {
        RecordRequest request = new RecordRequest();
        request.setFullName(fullName);
        request.setEmail(email);
        request.setPhone(phone);
        request.setAddress(address);
        return request;
    }
}
