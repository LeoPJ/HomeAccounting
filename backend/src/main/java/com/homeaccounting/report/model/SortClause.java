package com.homeaccounting.report.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** 排序键为维度名（与 dimensions 中一致）或指标 alias */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SortClause(String key, String dir) {}
