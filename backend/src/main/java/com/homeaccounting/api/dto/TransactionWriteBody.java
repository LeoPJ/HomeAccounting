package com.homeaccounting.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record TransactionWriteBody(
    @NotNull Long ledgerId,
    Long fundAccountId,
    @NotNull Long categoryId,
    @NotBlank @Size(max = 16) String type,
    @NotNull @DecimalMin(value = "0.0001", inclusive = true) BigDecimal amount,
    @NotNull LocalDateTime occurredAt,
    @Size(max = 512) String note,
    List<Long> tagIds) {

  public TransactionWriteBody {
    if (tagIds == null) {
      tagIds = List.of();
    } else {
      tagIds = List.copyOf(tagIds);
    }
    if (type != null) {
      type = type.trim();
    }
    if (note != null) {
      note = note.trim();
      if (note.isEmpty()) {
        note = null;
      }
    }
  }
}
