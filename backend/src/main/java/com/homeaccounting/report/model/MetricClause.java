package com.homeaccounting.report.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 聚合指标。
 *
 * @param alias 结果列名（字母数字下划线）
 * @param fn sum/min/max/avg/count_rows/count_distinct_tx
 * @param field sum/min/max/avg 时须为 amount；计数类可省略
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MetricClause(String alias, String fn, String field) {}
