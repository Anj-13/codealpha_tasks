package com.dataredundancy.removalsystem.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
    indexes = {
        @Index(name = "idx_customer_record_email", columnList = "email"),
        @Index(name = "idx_customer_record_phone", columnList = "phone")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_customer_record_fingerprint", columnNames = "finger_print_hash")
    }
)
public class CustomerRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String fullname;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(length = 255)
    private String address;

    @Column(name = "finger_print_hash", nullable = false, length = 64, unique = true)
    private String fingerPrintHash;

    @Enumerated(EnumType.STRING)
    private RecordStatus status;
    
    @Column(length = 255)
    private String validationReason;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public CustomerRecord() {}

    public CustomerRecord(String fullname, String email, String phone, String address) {
        this.fullname = fullname;
        this.email = email;
        this.phone = phone;
        this.address = address;
    }

    @PrePersist
    public void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {return id;}
    public String getFullname() {return fullname;}
    public String getPhone() {return phone;}
    public String getEmail() {return email;}
    public String getAddress() {return address;}

    public String getFingerprintHash() {return fingerPrintHash;}
    public RecordStatus getStatus() {return status;}
    public String getValidationReason() {return validationReason;}
    
    public void setId(Long id) {this.id = id;}
    public void setFullname(String fullname) {this.fullname = fullname;}
    public void setPhone(String phone) {this.phone = phone;}
    public void setEmail(String email) {this.email = email;}
    public void setAddress(String address) {this.address = address;}

    public void setFingerprintHash(String fingerPrintHash) {this.fingerPrintHash = fingerPrintHash;}
    public void setStatus(RecordStatus status) {this.status = status;}
    public void setValidationReason(String validationReason) {this.validationReason = validationReason;}

    public LocalDateTime getCreatedAt() {return createdAt;}
    public LocalDateTime getUpdatedAt() {return updatedAt;}
}