package com.homeaccounting.report;

import com.homeaccounting.api.ApiException;
import com.homeaccounting.category.CategoryService;
import com.homeaccounting.report.model.FilterClause;
import com.homeaccounting.report.model.MetricClause;
import com.homeaccounting.report.model.ReportDefinitionPayload;
import com.homeaccounting.report.model.SortClause;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class ReportDefinitionValidator {

  private static final Pattern ALIAS = Pattern.compile("[a-zA-Z][a-zA-Z0-9_]{0,63}");

  private ReportDefinitionValidator() {}

  public static void validate(ReportDefinitionPayload p) {
    if (p.filters().size() > ReportSchemaCatalog.MAX_FILTERS) {
      throw ApiException.badRequest("filters 最多 " + ReportSchemaCatalog.MAX_FILTERS + " 条");
    }
    if (p.dimensions().size() > ReportSchemaCatalog.MAX_DIMENSIONS) {
      throw ApiException.badRequest("dimensions 最多 " + ReportSchemaCatalog.MAX_DIMENSIONS + " 个");
    }
    if (p.metrics().isEmpty()) {
      throw ApiException.badRequest("metrics 至少一项");
    }
    if (p.metrics().size() > ReportSchemaCatalog.MAX_METRICS) {
      throw ApiException.badRequest("metrics 最多 " + ReportSchemaCatalog.MAX_METRICS + " 项");
    }
    if (p.sort().size() > ReportSchemaCatalog.MAX_SORT) {
      throw ApiException.badRequest("sort 最多 " + ReportSchemaCatalog.MAX_SORT + " 项");
    }

    Set<String> dimSet = new HashSet<>();
    for (String dim : p.dimensions()) {
      if (!ReportSchemaCatalog.DIMENSION_IDS.contains(dim)) {
        throw ApiException.badRequest("非法 dimension: " + dim);
      }
      if (!dimSet.add(dim)) {
        throw ApiException.badRequest("dimensions 不可重复: " + dim);
      }
    }

    Set<String> metricAliases = new HashSet<>();
    for (MetricClause m : p.metrics()) {
      if (m.alias() == null || !ALIAS.matcher(m.alias()).matches()) {
        throw ApiException.badRequest("非法 metric.alias: " + m.alias());
      }
      if (!metricAliases.add(m.alias())) {
        throw ApiException.badRequest("metrics.alias 不可重复: " + m.alias());
      }
      String fn = m.fn() == null ? "" : m.fn().trim().toLowerCase(Locale.ROOT);
      if (!ReportSchemaCatalog.METRIC_FN_IDS.contains(fn)) {
        throw ApiException.badRequest("非法 metric.fn: " + m.fn());
      }
      boolean needsAmount =
          fn.equals("sum") || fn.equals("min") || fn.equals("max") || fn.equals("avg");
      if (needsAmount) {
        if (m.field() == null || !"amount".equals(m.field())) {
          throw ApiException.badRequest("metric.fn 为 " + fn + " 时 field 须为 amount");
        }
      }
    }

    for (FilterClause f : p.filters()) {
      validateFilter(f);
    }

    Set<String> sortKeys = new HashSet<>(dimSet);
    sortKeys.addAll(metricAliases);
    for (SortClause s : p.sort()) {
      if (s.key() == null || !sortKeys.contains(s.key())) {
        throw ApiException.badRequest("sort.key 须为已有 dimension 或 metric.alias: " + s.key());
      }
      if (s.dir() == null || s.dir().isBlank()) {
        throw ApiException.badRequest("sort.dir 不能为空");
      }
      String dir = s.dir().trim().toUpperCase(Locale.ROOT);
      if (!dir.equals("ASC") && !dir.equals("DESC")) {
        throw ApiException.badRequest("sort.dir 须为 ASC 或 DESC");
      }
    }

    int lim = effectiveLimit(p);
    if (lim < 1) {
      throw ApiException.badRequest("limit 须 >= 1");
    }
  }

  public static int effectiveLimit(ReportDefinitionPayload p) {
    int lim = p.limit() == null ? ReportSchemaCatalog.DEFAULT_LIMIT : p.limit();
    return Math.min(Math.max(lim, 1), ReportSchemaCatalog.MAX_LIMIT);
  }

  private static void validateFilter(FilterClause f) {
    if (f.field() == null || !ReportSchemaCatalog.FILTER_FIELD_IDS.contains(f.field())) {
      throw ApiException.badRequest("非法 filter.field: " + f.field());
    }
    String op = f.op() == null ? "" : f.op().trim().toLowerCase(Locale.ROOT);
    if (!ReportSchemaCatalog.ALL_FILTER_OPS.contains(op)) {
      throw ApiException.badRequest("非法 filter.op: " + f.op());
    }

    switch (f.field()) {
      case "type" -> validateTypeFilter(op, f);
      case "occurred_at" -> validateOccurredAtFilter(op, f);
      case "ledger_id",
              "fund_account_id",
              "category_id",
              "created_by",
              "tag_id" -> validateIdFilter(op, f);
      default -> throw ApiException.badRequest("未支持的 filter.field: " + f.field());
    }
  }

  private static void validateTypeFilter(String op, FilterClause f) {
    if (op.equals("is_null") || op.equals("is_not_null")) {
      if (!f.params().isEmpty()) {
        throw ApiException.badRequest("type 与 op=" + op + " 不应带参数");
      }
      return;
    }
    if (op.equals("eq") || op.equals("ne")) {
      if (f.params().size() != 1) {
        throw ApiException.badRequest("type 筛选 eq/ne 须 1 个参数");
      }
      String v = String.valueOf(f.params().get(0)).trim();
      if (!CategoryService.TYPE_EXPENSE.equals(v) && !CategoryService.TYPE_INCOME.equals(v)) {
        throw ApiException.badRequest("type 参数须为 EXPENSE 或 INCOME");
      }
      return;
    }
    if (op.equals("in")) {
      if (f.params().isEmpty()) {
        throw ApiException.badRequest("type in 至少 1 个取值");
      }
      for (Object o : f.params()) {
        String v = String.valueOf(o).trim();
        if (!CategoryService.TYPE_EXPENSE.equals(v) && !CategoryService.TYPE_INCOME.equals(v)) {
          throw ApiException.badRequest("type in 参数须为 EXPENSE 或 INCOME");
        }
      }
      return;
    }
    throw ApiException.badRequest("type 不支持 op=" + op);
  }

  private static void validateOccurredAtFilter(String op, FilterClause f) {
    switch (op) {
      case "eq", "ne", "gt", "gte", "lt", "lte" -> {
        if (f.params().size() != 1) {
          throw ApiException.badRequest("occurred_at 比较运算须 1 个时间参数");
        }
      }
      case "between" -> {
        if (f.params().size() != 2) {
          throw ApiException.badRequest("occurred_at between 须 2 个时间参数");
        }
      }
      case "is_null", "is_not_null" -> {
        if (!f.params().isEmpty()) {
          throw ApiException.badRequest("occurred_at 与 op=" + op + " 不应带参数");
        }
      }
      default -> throw ApiException.badRequest("occurred_at 不支持 op=" + op);
    }
  }

  private static void validateIdFilter(String op, FilterClause f) {
    switch (op) {
      case "eq", "ne" -> {
        if (f.params().size() != 1) {
          throw ApiException.badRequest(f.field() + " eq/ne 须 1 个 ID 参数");
        }
      }
      case "in" -> {
        if (f.params().isEmpty()) {
          throw ApiException.badRequest(f.field() + " in 至少 1 个 ID");
        }
      }
      case "is_null", "is_not_null" -> {
        if (!f.field().equals("fund_account_id")) {
          throw ApiException.badRequest(f.field() + " 不支持 is_null");
        }
        if (!f.params().isEmpty()) {
          throw ApiException.badRequest("is_null / is_not_null 不应带参数");
        }
      }
      default ->
          throw ApiException.badRequest(f.field() + " 不支持 op=" + op + "（可用 eq/ne/in，fund_account_id 另支持 is_null/is_not_null）");
    }
  }
}
