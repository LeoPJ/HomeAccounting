package com.homeaccounting.report.schema;

public record SchemaLimits(
    int maxFilters,
    int maxDimensions,
    int maxMetrics,
    int maxSort,
    int defaultLimit,
    int maxLimit) {}
