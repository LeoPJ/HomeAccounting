package com.homeaccounting.report;

import com.homeaccounting.api.ApiException;
import com.homeaccounting.category.CategoryService;
import com.homeaccounting.report.model.FilterClause;
import com.homeaccounting.report.model.MetricClause;
import com.homeaccounting.report.model.ReportDefinitionPayload;
import com.homeaccounting.report.model.SortClause;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** 根据白名单定义拼装 SQL，仅数值与时间走占位符绑定 */
public final class ReportSqlBuilder {

  private ReportSqlBuilder() {}

  public record BuiltSql(String sql, List<Object> params, int limit) {}

  public static BuiltSql build(ReportDefinitionPayload def, long householdId) {
    ReportDefinitionValidator.validate(def);
    int limit = ReportDefinitionValidator.effectiveLimit(def);

    boolean tagJoin = def.dimensions().contains("tag_id");

    List<String> selectDims = new ArrayList<>();
    for (String dim : def.dimensions()) {
      selectDims.add(dimensionExpr(dim, tagJoin) + " AS " + quoteIdent(dim));
    }

    List<String> selectMetrics = new ArrayList<>();
    for (MetricClause m : def.metrics()) {
      selectMetrics.add(metricExpr(m, tagJoin) + " AS " + quoteIdent(m.alias()));
    }

    List<String> selectAll = new ArrayList<>();
    selectAll.addAll(selectDims);
    selectAll.addAll(selectMetrics);

    StringBuilder sql = new StringBuilder(256);
    sql.append("SELECT ").append(String.join(", ", selectAll));
    sql.append(" FROM transactions t ");
    if (tagJoin) {
      sql.append(" INNER JOIN transaction_tags tt ON tt.transaction_id = t.id ");
    }

    List<Object> params = new ArrayList<>();
    StringBuilder where = new StringBuilder(" WHERE t.household_id = ? ");
    params.add(householdId);

    for (FilterClause f : def.filters()) {
      where.append(" AND ");
      appendFilter(f, where, params);
    }

    sql.append(where);
    if (!def.dimensions().isEmpty()) {
      sql.append(" GROUP BY ");
      sql.append(
          def.dimensions().stream()
              .map(d -> dimensionExpr(d, tagJoin))
              .collect(Collectors.joining(", ")));
    }

    if (!def.sort().isEmpty()) {
      sql.append(" ORDER BY ");
      sql.append(
          def.sort().stream()
              .map(
                  s ->
                      quoteIdent(s.key())
                          + " "
                          + s.dir().trim().toUpperCase(Locale.ROOT))
              .collect(Collectors.joining(", ")));
    }

    sql.append(" LIMIT ? ");
    params.add(limit);

    return new BuiltSql(sql.toString(), params, limit);
  }

  /**
   * 与聚合查询相同的筛选范围，拉取命中流水明细（按发生时间倒序），用于前端对照汇总。
   */
  public static BuiltSql buildTransactionDetailSql(
      ReportDefinitionPayload def, long householdId, int detailLimit) {
    ReportDefinitionValidator.validate(def);
    int lim = Math.min(Math.max(detailLimit, 1), 200);
    StringBuilder sql = new StringBuilder();
    sql.append(
        "SELECT t.id, t.ledger_id, t.fund_account_id, t.category_id, t.type, ");
    sql.append("t.amount, t.occurred_at, t.note, t.created_by ");
    sql.append("FROM transactions t ");
    List<Object> params = new ArrayList<>();
    StringBuilder where = new StringBuilder(" WHERE t.household_id = ? ");
    params.add(householdId);
    for (FilterClause f : def.filters()) {
      where.append(" AND ");
      appendFilter(f, where, params);
    }
    sql.append(where);
    sql.append(" ORDER BY t.occurred_at DESC LIMIT ? ");
    params.add(lim);
    return new BuiltSql(sql.toString(), params, lim);
  }

  private static String quoteIdent(String name) {
    if (name == null || name.isEmpty()) {
      throw ApiException.badRequest("标识符不能为空");
    }
    return "`" + name.replace("`", "") + "`";
  }

  private static String dimensionExpr(String dim, boolean tagJoin) {
    return switch (dim) {
      case "ledger_id" -> "t.ledger_id";
      case "category_id" -> "t.category_id";
      case "fund_account_id" -> "t.fund_account_id";
      case "type" -> "t.type";
      case "created_by" -> "t.created_by";
      case "tag_id" -> {
        if (!tagJoin) {
          throw ApiException.badRequest("dimension tag_id 需要 JOIN transaction_tags（自动），请联系服务端");
        }
        yield "tt.tag_id";
      }
      case "month" -> "DATE_FORMAT(t.occurred_at, '%Y-%m')";
      case "day" -> "DATE(t.occurred_at)";
      case "year" -> "YEAR(t.occurred_at)";
      default -> throw ApiException.badRequest("未知 dimension: " + dim);
    };
  }

  private static String metricExpr(MetricClause m, boolean tagJoin) {
    String fn = m.fn().trim().toLowerCase(Locale.ROOT);
    return switch (fn) {
      case "sum" -> "SUM(t.amount)";
      case "min" -> "MIN(t.amount)";
      case "max" -> "MAX(t.amount)";
      case "avg" -> "AVG(t.amount)";
      case "count_rows" -> tagJoin ? "COUNT(DISTINCT t.id)" : "COUNT(*)";
      case "count_distinct_tx" -> "COUNT(DISTINCT t.id)";
      default -> throw ApiException.badRequest("未知 metric.fn: " + m.fn());
    };
  }

  private static void appendFilter(FilterClause f, StringBuilder where, List<Object> params) {
    String op = f.op().trim().toLowerCase(Locale.ROOT);
    switch (f.field()) {
      case "occurred_at" -> appendOccurredAt(f, op, where, params);
      case "type" -> appendType(f, op, where, params);
      case "ledger_id" -> appendLongColumn("t.ledger_id", f, op, where, params);
      case "fund_account_id" -> appendFundAccount(f, op, where, params);
      case "category_id" -> appendLongColumn("t.category_id", f, op, where, params);
      case "created_by" -> appendLongColumn("t.created_by", f, op, where, params);
      case "tag_id" -> appendTag(f, op, where, params);
      default -> throw ApiException.badRequest("未实现的 filter: " + f.field());
    }
  }

  private static void appendType(FilterClause f, String op, StringBuilder where, List<Object> params) {
    switch (op) {
      case "eq" -> {
        where.append("t.type = ? ");
        params.add(normalizeTypeParam(f.params().get(0)));
      }
      case "ne" -> {
        where.append("t.type <> ? ");
        params.add(normalizeTypeParam(f.params().get(0)));
      }
      case "in" -> {
        where.append("t.type IN (");
        where.append("?, ".repeat(f.params().size() - 1)).append("?) ");
        for (Object p : f.params()) {
          params.add(normalizeTypeParam(p));
        }
      }
      case "is_null" -> where.append("t.type IS NULL ");
      case "is_not_null" -> where.append("t.type IS NOT NULL ");
      default -> throw ApiException.badRequest("type filter op 异常: " + op);
    }
  }

  private static String normalizeTypeParam(Object p) {
    String v = String.valueOf(p).trim();
    if (!CategoryService.TYPE_EXPENSE.equals(v) && !CategoryService.TYPE_INCOME.equals(v)) {
      throw ApiException.badRequest("type 参数须为 EXPENSE 或 INCOME");
    }
    return v;
  }

  private static void appendOccurredAt(
      FilterClause f, String op, StringBuilder where, List<Object> params) {
    switch (op) {
      case "eq" -> {
        where.append("t.occurred_at = ? ");
        params.add(parseDateTime(f.params().get(0)));
      }
      case "ne" -> {
        where.append("t.occurred_at <> ? ");
        params.add(parseDateTime(f.params().get(0)));
      }
      case "gt" -> {
        where.append("t.occurred_at > ? ");
        params.add(parseDateTime(f.params().get(0)));
      }
      case "gte" -> {
        where.append("t.occurred_at >= ? ");
        params.add(parseDateTime(f.params().get(0)));
      }
      case "lt" -> {
        where.append("t.occurred_at < ? ");
        params.add(parseDateTime(f.params().get(0)));
      }
      case "lte" -> {
        where.append("t.occurred_at <= ? ");
        params.add(parseDateTime(f.params().get(0)));
      }
      case "between" -> {
        where.append("t.occurred_at BETWEEN ? AND ? ");
        params.add(parseDateTime(f.params().get(0)));
        params.add(parseDateTime(f.params().get(1)));
      }
      case "is_null" -> where.append("t.occurred_at IS NULL ");
      case "is_not_null" -> where.append("t.occurred_at IS NOT NULL ");
      default -> throw ApiException.badRequest("occurred_at filter op 异常: " + op);
    }
  }

  private static Timestamp parseDateTime(Object raw) {
    if (raw instanceof Timestamp ts) {
      return ts;
    }
    if (raw instanceof LocalDateTime ldt) {
      return Timestamp.valueOf(ldt);
    }
    String s = String.valueOf(raw).trim();
    try {
      return Timestamp.valueOf(LocalDateTime.parse(s));
    } catch (DateTimeParseException e) {
      throw ApiException.badRequest("无法解析时间: " + s);
    }
  }

  private static void appendLongColumn(
      String col, FilterClause f, String op, StringBuilder where, List<Object> params) {
    switch (op) {
      case "eq" -> {
        where.append(col).append(" = ? ");
        params.add(toLong(f.params().get(0)));
      }
      case "ne" -> {
        where.append(col).append(" <> ? ");
        params.add(toLong(f.params().get(0)));
      }
      case "in" -> {
        where.append(col).append(" IN (");
        where.append("?, ".repeat(f.params().size() - 1)).append("?) ");
        for (Object p : f.params()) {
          params.add(toLong(p));
        }
      }
      default -> throw ApiException.badRequest(col + " filter op 异常: " + op);
    }
  }

  private static void appendFundAccount(
      FilterClause f, String op, StringBuilder where, List<Object> params) {
    switch (op) {
      case "eq" -> {
        where.append("t.fund_account_id = ? ");
        params.add(toLong(f.params().get(0)));
      }
      case "ne" -> {
        where.append("t.fund_account_id <> ? ");
        params.add(toLong(f.params().get(0)));
      }
      case "in" -> {
        where.append("t.fund_account_id IN (");
        where.append("?, ".repeat(f.params().size() - 1)).append("?) ");
        for (Object p : f.params()) {
          params.add(toLong(p));
        }
      }
      case "is_null" -> where.append("t.fund_account_id IS NULL ");
      case "is_not_null" -> where.append("t.fund_account_id IS NOT NULL ");
      default -> throw ApiException.badRequest("fund_account_id filter op 异常: " + op);
    }
  }

  private static void appendTag(FilterClause f, String op, StringBuilder where, List<Object> params) {
    switch (op) {
      case "eq" -> {
        where.append(
            "EXISTS (SELECT 1 FROM transaction_tags tt_f WHERE tt_f.transaction_id = t.id AND tt_f.tag_id = ?) ");
        params.add(toLong(f.params().get(0)));
      }
      case "ne" -> {
        where.append(
            "NOT EXISTS (SELECT 1 FROM transaction_tags tt_f WHERE tt_f.transaction_id = t.id AND tt_f.tag_id = ?) ");
        params.add(toLong(f.params().get(0)));
      }
      case "in" -> {
        where.append(
            "EXISTS (SELECT 1 FROM transaction_tags tt_f WHERE tt_f.transaction_id = t.id AND tt_f.tag_id IN (");
        where.append("?, ".repeat(f.params().size() - 1)).append("?)) ");
        for (Object p : f.params()) {
          params.add(toLong(p));
        }
      }
      default -> throw ApiException.badRequest("tag_id 仅支持 eq / ne / in");
    }
  }

  private static long toLong(Object raw) {
    if (raw instanceof Number n) {
      return n.longValue();
    }
    String s = String.valueOf(raw).trim();
    try {
      return Long.parseLong(s);
    } catch (NumberFormatException e) {
      throw ApiException.badRequest("非法 ID 数值: " + s);
    }
  }
}
