package com.homeaccounting.report.schema;

import java.util.List;

public record ReportSchemaResponse(
    String version,
    SchemaLimits limits,
    List<FilterFieldSchema> filterFields,
    List<DimensionSchema> dimensions,
    List<MetricFnSchema> metricFunctions,
    List<String> sortDirections,
    List<String> transactionTypes,
    String notes) {}
