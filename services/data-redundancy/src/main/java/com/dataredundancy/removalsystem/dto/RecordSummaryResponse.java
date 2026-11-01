package com.dataredundancy.removalsystem.dto;

import java.time.LocalDateTime;

public record RecordSummaryResponse(long totalRecords, LocalDateTime lastStoredAt) {
}
