// This is Output DTO for API request
package com.dataredundancy.removalsystem.dto;

import com.dataredundancy.removalsystem.model.RecordStatus;
import java.time.LocalDateTime;

public class RecordResponse {
    
    private Long id;
    private String fullName;
    private String email;
    private String phone;
    private String address;
    private RecordStatus status;
    private String validationReason;
    private boolean stored;
    private boolean masked;
    private LocalDateTime createdAt;

    public Long getId() {return id;}
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getAddress() { return address; }
    public RecordStatus getStatus() { return status;}
    public String getValidationReason() { return validationReason; }
    public boolean isStored() { return stored; }
    public boolean isMasked() { return masked; }
    public LocalDateTime getCreatedAt() { return createdAt;}

    public void setId(Long id) { this.id = id; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public void setEmail(String email) { this.email = email; }
    public void setPhone(String phone) { this.phone = phone; }
    public void setAddress(String address) { this.address = address; }
    public void setStatus(RecordStatus status) { this.status = status;}
    public void setValidationReason(String validationReason) { this.validationReason = validationReason; }
    public void setStored(boolean stored) { this.stored = stored; }
    public void setMasked(boolean masked) { this.masked = masked; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt;}
    
}