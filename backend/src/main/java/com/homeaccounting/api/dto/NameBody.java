package com.homeaccounting.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NameBody(@NotBlank @Size(max = 64) String name) {}
