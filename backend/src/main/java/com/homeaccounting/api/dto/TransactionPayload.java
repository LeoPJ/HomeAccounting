package com.homeaccounting.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record TransactionPayload(
    long id,
    long householdId,
    long ledgerId,
    Long fundAccountId,
    long categoryId,
    String type,
    BigDecimal amount,
    LocalDateTime occurredAt,
    String note,
    long createdBy,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    List<Long> tagIds) {}
