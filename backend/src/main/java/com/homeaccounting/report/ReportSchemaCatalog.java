package com.homeaccounting.report;

import com.homeaccounting.category.CategoryService;
import com.homeaccounting.report.schema.DimensionSchema;
import com.homeaccounting.report.schema.FilterFieldSchema;
import com.homeaccounting.report.schema.MetricFnSchema;
import com.homeaccounting.report.schema.ReportSchemaResponse;
import com.homeaccounting.report.schema.SchemaLimits;
import java.util.List;
import java.util.Set;

/**
 * 报表 DSL 白名单：校验逻辑与 {@link ReportSchemaResponse} 共用同一数据源，避免文档与实现漂移。
 */
public final class ReportSchemaCatalog {

  public static final int MAX_FILTERS = 24;
  public static final int MAX_DIMENSIONS = 12;
  public static final int MAX_METRICS = 16;
  public static final int MAX_SORT = 12;
  public static final int DEFAULT_LIMIT = 500;
  public static final int MAX_LIMIT = 2000;

  public static final Set<String> FILTER_FIELD_IDS =
      Set.of(
          "occurred_at",
          "type",
          "ledger_id",
          "fund_account_id",
          "category_id",
          "created_by",
          "tag_id");

  public static final Set<String> DIMENSION_IDS =
      Set.of(
          "ledger_id",
          "category_id",
          "fund_account_id",
          "type",
          "created_by",
          "tag_id",
          "month",
          "day",
          "year");

  /** 校验 filter.op 合法字符集（各字段可用子集由校验逻辑约束） */
  public static final Set<String> ALL_FILTER_OPS =
      Set.of("eq", "ne", "gt", "gte", "lt", "lte", "between", "in", "is_null", "is_not_null");

  public static final Set<String> METRIC_FN_IDS =
      Set.of("sum", "min", "max", "avg", "count_rows", "count_distinct_tx");

  private static final List<String> OPS_DATETIME_CMP =
      List.of("eq", "ne", "gt", "gte", "lt", "lte", "between", "is_null", "is_not_null");
  private static final List<String> OPS_ENUM_TYPE =
      List.of("eq", "ne", "in", "is_null", "is_not_null");
  private static final List<String> OPS_LONG =
      List.of("eq", "ne", "in");
  private static final List<String> OPS_FUND =
      List.of("eq", "ne", "in", "is_null", "is_not_null");
  private static final List<String> OPS_TAG = List.of("eq", "ne", "in");

  private ReportSchemaCatalog() {}

  public static ReportSchemaResponse buildResponse() {
    String note =
        "filters.params：occurred_at 为 ISO-8601 本地时间字符串；ID 类为数字；type 仅 EXPENSE/INCOME。"
            + " dimensions 含 tag_id 时会 JOIN transaction_tags。"
            + " metric.alias 须字母开头，字母数字下划线，≤64。";
    return new ReportSchemaResponse(
        "1.0",
        new SchemaLimits(MAX_FILTERS, MAX_DIMENSIONS, MAX_METRICS, MAX_SORT, DEFAULT_LIMIT, MAX_LIMIT),
        List.of(
            new FilterFieldSchema("occurred_at", "发生时间", "datetime", OPS_DATETIME_CMP),
            new FilterFieldSchema(
                "type",
                "收支类型",
                "enum",
                OPS_ENUM_TYPE,
                List.of(CategoryService.TYPE_EXPENSE, CategoryService.TYPE_INCOME)),
            new FilterFieldSchema("ledger_id", "账本", "long", OPS_LONG),
            new FilterFieldSchema("fund_account_id", "资金账号", "long", OPS_FUND),
            new FilterFieldSchema("category_id", "分类", "long", OPS_LONG),
            new FilterFieldSchema("created_by", "记账人用户ID", "long", OPS_LONG),
            new FilterFieldSchema("tag_id", "标签（EXISTS）", "tag", OPS_TAG)),
        List.of(
            new DimensionSchema("ledger_id", "账本", false),
            new DimensionSchema("category_id", "分类", false),
            new DimensionSchema("fund_account_id", "资金账号", false),
            new DimensionSchema("type", "收支类型", false),
            new DimensionSchema("created_by", "记账人", false),
            new DimensionSchema("tag_id", "标签", true),
            new DimensionSchema("month", "月（YYYY-MM）", false),
            new DimensionSchema("day", "日（DATE）", false),
            new DimensionSchema("year", "年", false)),
        List.of(
            new MetricFnSchema("sum", "求和", true, List.of("amount")),
            new MetricFnSchema("min", "最小", true, List.of("amount")),
            new MetricFnSchema("max", "最大", true, List.of("amount")),
            new MetricFnSchema("avg", "平均", true, List.of("amount")),
            new MetricFnSchema("count_rows", "计数（有 tag 维度时用 DISTINCT 流水）", false, List.of()),
            new MetricFnSchema("count_distinct_tx", "去重流水条数", false, List.of())),
        List.of("ASC", "DESC"),
        List.of(CategoryService.TYPE_EXPENSE, CategoryService.TYPE_INCOME),
        note);
  }
}
