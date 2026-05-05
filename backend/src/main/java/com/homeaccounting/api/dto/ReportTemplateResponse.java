package com.homeaccounting.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;

public record ReportTemplateResponse(
    long id,
    long householdId,
    String name,
    JsonNode definition,
    long createdBy,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {}
