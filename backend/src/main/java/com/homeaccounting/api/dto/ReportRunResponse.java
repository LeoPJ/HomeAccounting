package com.homeaccounting.api.dto;

import java.util.List;
import java.util.Map;

/**
 * summaryRows：聚合结果，维度 ID 已转为中文列名与可读取值；detailRows：命中流水明细；chartPieSlices：单维度+单指标时的饼图数据。
 */
public record ReportRunResponse(
    List<Map<String, Object>> summaryRows,
    List<Map<String, Object>> detailRows,
    List<Map<String, Object>> chartPieSlices,
    int limit,
    int detailLimit) {}
