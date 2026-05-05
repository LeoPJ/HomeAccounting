package com.homeaccounting.report.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;

/**
 * 报表定义（存于模板 JSON 或随请求内联）。未知字段反序列化时忽略，由服务端校验语义。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReportDefinitionPayload(
    List<FilterClause> filters,
    List<String> dimensions,
    List<MetricClause> metrics,
    List<SortClause> sort,
    Integer limit) {

  public ReportDefinitionPayload {
    filters = filters != null ? List.copyOf(filters) : List.of();
    dimensions = dimensions != null ? List.copyOf(dimensions) : List.of();
    metrics = metrics != null ? List.copyOf(metrics) : List.of();
    sort = sort != null ? List.copyOf(sort) : List.of();
  }

  /** 模板定义与运行时附加筛选合并（附加在后） */
  public ReportDefinitionPayload mergeExtraFilters(List<FilterClause> extra) {
    if (extra == null || extra.isEmpty()) {
      return this;
    }
    List<FilterClause> merged = new ArrayList<>(filters.size() + extra.size());
    merged.addAll(filters);
    merged.addAll(extra);
    return new ReportDefinitionPayload(merged, dimensions, metrics, sort, limit);
  }
}
