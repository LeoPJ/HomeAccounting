package com.homeaccounting.api.dto;

import com.homeaccounting.report.model.ReportDefinitionPayload;
import jakarta.validation.constraints.Size;

public record ReportTemplateUpdateRequest(
    @Size(max = 128) String name, ReportDefinitionPayload definition) {}
