package com.homeaccounting.report.schema;

import java.util.List;

public record MetricFnSchema(
    String fn,
    String label,
    boolean requiresMetricField,
    List<String> allowedMetricFields) {}
