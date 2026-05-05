package com.homeaccounting.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.homeaccounting.report.model.FilterClause;
import com.homeaccounting.report.model.ReportDefinitionPayload;
import java.util.List;

/**
 * 执行报表：任选模板 ID 或内联 definition；extraFilters 在模板基础上追加 AND 条件。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReportExecuteRequest(
    Long templateId,
    ReportDefinitionPayload definition,
    List<FilterClause> extraFilters) {

  public ReportExecuteRequest {
    extraFilters = extraFilters != null ? List.copyOf(extraFilters) : List.of();
  }
}
