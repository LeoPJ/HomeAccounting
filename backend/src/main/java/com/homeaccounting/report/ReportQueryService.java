package com.homeaccounting.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.homeaccounting.api.ApiException;
import com.homeaccounting.api.dto.ReportExecuteRequest;
import com.homeaccounting.api.dto.ReportRunResponse;
import com.homeaccounting.category.CategoryService;
import com.homeaccounting.entity.Category;
import com.homeaccounting.entity.Ledger;
import com.homeaccounting.entity.ReportTemplate;
import com.homeaccounting.entity.Tag;
import com.homeaccounting.entity.TransactionTag;
import com.homeaccounting.entity.User;
import com.homeaccounting.entity.FundAccount;
import com.homeaccounting.fund.FundAccountService;
import com.homeaccounting.household.HouseholdScopeService;
import com.homeaccounting.ledger.LedgerService;
import com.homeaccounting.mapper.ReportTemplateMapper;
import com.homeaccounting.mapper.TransactionTagMapper;
import com.homeaccounting.mapper.UserMapper;
import com.homeaccounting.entity.TransactionTagExample;
import com.homeaccounting.report.model.MetricClause;
import com.homeaccounting.report.model.ReportDefinitionPayload;
import com.homeaccounting.tag.TagService;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ReportQueryService {

  private static final int DETAIL_LIMIT = 100;

  private final JdbcTemplate jdbcTemplate;
  private final ReportTemplateMapper reportTemplateMapper;
  private final HouseholdScopeService householdScopeService;
  private final ObjectMapper objectMapper;
  private final LedgerService ledgerService;
  private final CategoryService categoryService;
  private final FundAccountService fundAccountService;
  private final TagService tagService;
  private final UserMapper userMapper;
  private final TransactionTagMapper transactionTagMapper;

  public ReportQueryService(
      JdbcTemplate jdbcTemplate,
      ReportTemplateMapper reportTemplateMapper,
      HouseholdScopeService householdScopeService,
      ObjectMapper objectMapper,
      LedgerService ledgerService,
      CategoryService categoryService,
      FundAccountService fundAccountService,
      TagService tagService,
      UserMapper userMapper,
      TransactionTagMapper transactionTagMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.reportTemplateMapper = reportTemplateMapper;
    this.householdScopeService = householdScopeService;
    this.objectMapper = objectMapper;
    this.ledgerService = ledgerService;
    this.categoryService = categoryService;
    this.fundAccountService = fundAccountService;
    this.tagService = tagService;
    this.userMapper = userMapper;
    this.transactionTagMapper = transactionTagMapper;
  }

  public ReportRunResponse run(long userId, ReportExecuteRequest req) {
    long hid = householdScopeService.requireHouseholdId(userId);
    ReportDefinitionPayload def = resolveDefinition(hid, req);
    LookupBundle lookups = buildLookupBundle(userId);

    ReportSqlBuilder.BuiltSql built = ReportSqlBuilder.build(def, hid);
    List<Map<String, Object>> rawSummary =
        jdbcTemplate.query(
            built.sql(),
            (rs, rowNum) -> mapRow(rs),
            built.params().toArray());
    List<Map<String, Object>> summaryRows = new ArrayList<>();
    for (Map<String, Object> raw : rawSummary) {
      summaryRows.add(humanizeSummaryRow(raw, lookups));
    }

    List<Map<String, Object>> chartPieSlices = buildChartPieSlices(def, summaryRows);

    ReportSqlBuilder.BuiltSql detailBuilt =
        ReportSqlBuilder.buildTransactionDetailSql(def, hid, DETAIL_LIMIT);
    List<Map<String, Object>> rawDetail =
        jdbcTemplate.query(
            detailBuilt.sql(),
            (rs, rowNum) -> mapRow(rs),
            detailBuilt.params().toArray());

    List<Long> txnIds = new ArrayList<>();
    for (Map<String, Object> r : rawDetail) {
      Object idObj = r.get("id");
      if (idObj instanceof Number n) {
        txnIds.add(n.longValue());
      }
    }
    Map<Long, String> txnTagLine = buildTxnTagLines(txnIds, lookups);

    List<Map<String, Object>> detailRows = new ArrayList<>();
    for (Map<String, Object> raw : rawDetail) {
      detailRows.add(humanizeDetailRow(raw, lookups, txnTagLine));
    }

    return new ReportRunResponse(summaryRows, detailRows, chartPieSlices, built.limit(), DETAIL_LIMIT);
  }

  private LookupBundle buildLookupBundle(long userId) {
    Map<Long, String> ledgerNames = new HashMap<>();
    for (Ledger l : ledgerService.list(userId)) {
      ledgerNames.put(l.getId(), l.getName());
    }
    Map<Long, String> categoryNames = new HashMap<>();
    for (Category c : categoryService.list(userId, null)) {
      categoryNames.put(c.getId(), c.getName());
    }
    Map<Long, String> fundNames = new HashMap<>();
    for (FundAccount f : fundAccountService.list(userId)) {
      fundNames.put(f.getId(), f.getName());
    }
    Map<Long, String> tagNames = new HashMap<>();
    for (Tag t : tagService.list(userId)) {
      tagNames.put(t.getId(), t.getName());
    }
    return new LookupBundle(ledgerNames, categoryNames, fundNames, tagNames);
  }

  private Map<Long, String> buildTxnTagLines(List<Long> txnIds, LookupBundle lookups) {
    Map<Long, String> result = new LinkedHashMap<>();
    if (txnIds.isEmpty()) {
      return result;
    }
    TransactionTagExample ex = new TransactionTagExample();
    ex.createCriteria().andTransactionIdIn(txnIds);
    Map<Long, List<Long>> byTxn = new LinkedHashMap<>();
    for (TransactionTag tt : transactionTagMapper.selectByExample(ex)) {
      byTxn
          .computeIfAbsent(tt.getTransactionId(), k -> new ArrayList<>())
          .add(tt.getTagId());
    }
    for (Map.Entry<Long, List<Long>> e : byTxn.entrySet()) {
      List<String> names = new ArrayList<>();
      for (Long tid : e.getValue()) {
        names.add(lookups.tagNames().getOrDefault(tid, "#" + tid));
      }
      result.put(e.getKey(), String.join("、", names));
    }
    return result;
  }

  private Map<String, Object> humanizeSummaryRow(Map<String, Object> raw, LookupBundle lookups) {
    Map<String, Object> out = new LinkedHashMap<>();
    for (Map.Entry<String, Object> e : raw.entrySet()) {
      String key = e.getKey();
      Object v = e.getValue();
      String nk = key == null ? "" : key.trim();
      switch (nk) {
        case "ledger_id" ->
            out.put("账本", lookups.ledgerNames().getOrDefault(toLong(v), "（未知账本）"));
        case "category_id" ->
            out.put("分类", lookups.categoryNames().getOrDefault(toLong(v), "（未知分类）"));
        case "fund_account_id" ->
            out.put(
                "资金账户",
                v == null ? "（未指定）" : lookups.fundNames().getOrDefault(toLong(v), "（未知账户）"));
        case "type" -> out.put("收支类型", typeZh(v));
        case "created_by" -> out.put("记账人", userLabel(toLong(v)));
        case "tag_id" ->
            out.put("标签", lookups.tagNames().getOrDefault(toLong(v), "（未知标签）"));
        case "month" -> out.put("月份", v);
        case "day" -> out.put("日期", v);
        case "year" -> out.put("年份", v);
        default -> out.put(key, v);
      }
    }
    return out;
  }

  private Map<String, Object> humanizeDetailRow(
      Map<String, Object> raw, LookupBundle lookups, Map<Long, String> txnTagLine) {
    Map<String, Object> out = new LinkedHashMap<>();
    long id = toLong(raw.get("id"));
    out.put("id", id);
    out.put("账本", lookups.ledgerNames().getOrDefault(toLong(raw.get("ledger_id")), "（未知）"));
    out.put("分类", lookups.categoryNames().getOrDefault(toLong(raw.get("category_id")), "（未知）"));
    Object fundRaw = raw.get("fund_account_id");
    out.put(
        "资金账户",
        fundRaw == null ? "（未指定）" : lookups.fundNames().getOrDefault(toLong(fundRaw), "（未知）"));
    out.put("收支类型", typeZh(raw.get("type")));
    out.put("金额", raw.get("amount"));
    out.put("发生时间", formatOccurred(raw.get("occurred_at")));
    Object note = raw.get("note");
    out.put("备注", note == null ? "" : String.valueOf(note));
    out.put("记账人", userLabel(toLong(raw.get("created_by"))));
    out.put("标签", txnTagLine.getOrDefault(id, ""));
    return out;
  }

  private String userLabel(long userId) {
    if (userId <= 0) {
      return "—";
    }
    User u = userMapper.selectByPrimaryKey(userId);
    if (u == null) {
      return "用户#" + userId;
    }
    if (u.getNickname() != null && !u.getNickname().isBlank()) {
      return u.getNickname().trim();
    }
    return "用户#" + userId;
  }

  private static String typeZh(Object v) {
    if (v == null) {
      return "—";
    }
    String s = String.valueOf(v).trim();
    if (CategoryService.TYPE_INCOME.equals(s)) {
      return "收入";
    }
    if (CategoryService.TYPE_EXPENSE.equals(s)) {
      return "支出";
    }
    return s;
  }

  private static String formatOccurred(Object v) {
    if (v == null) {
      return "";
    }
    if (v instanceof Timestamp ts) {
      return ts.toLocalDateTime().toString().replace('T', ' ');
    }
    if (v instanceof LocalDateTime ldt) {
      return ldt.toString().replace('T', ' ');
    }
    return String.valueOf(v);
  }

  private static long toLong(Object v) {
    if (v == null) {
      return 0L;
    }
    if (v instanceof Number n) {
      return n.longValue();
    }
    return Long.parseLong(String.valueOf(v).trim());
  }

  private List<Map<String, Object>> buildChartPieSlices(
      ReportDefinitionPayload def, List<Map<String, Object>> summaryRows) {
    if (def.dimensions().size() != 1 || def.metrics().size() != 1 || summaryRows.isEmpty()) {
      return List.of();
    }
    String dim = def.dimensions().get(0);
    String dimTitle = dimensionTitleZh(dim);
    String metricAlias = def.metrics().get(0).alias();
    List<Map<String, Object>> slices = new ArrayList<>();
    for (Map<String, Object> row : summaryRows) {
      Object label = row.get(dimTitle);
      Object val = row.get(metricAlias);
      if (label == null || val == null) {
        continue;
      }
      BigDecimal num = toBigDecimal(val);
      if (num == null) {
        continue;
      }
      Map<String, Object> one = new LinkedHashMap<>();
      one.put("label", String.valueOf(label));
      one.put("value", num);
      slices.add(one);
    }
    return slices;
  }

  private static String dimensionTitleZh(String dim) {
    return switch (dim) {
      case "ledger_id" -> "账本";
      case "category_id" -> "分类";
      case "fund_account_id" -> "资金账户";
      case "type" -> "收支类型";
      case "created_by" -> "记账人";
      case "tag_id" -> "标签";
      case "month" -> "月份";
      case "day" -> "日期";
      case "year" -> "年份";
      default -> dim;
    };
  }

  private static BigDecimal toBigDecimal(Object v) {
    if (v instanceof BigDecimal b) {
      return b;
    }
    if (v instanceof Number n) {
      return BigDecimal.valueOf(n.doubleValue());
    }
    try {
      return new BigDecimal(String.valueOf(v));
    } catch (Exception e) {
      return null;
    }
  }

  private ReportDefinitionPayload resolveDefinition(long hid, ReportExecuteRequest req) {
    boolean hasTpl = req.templateId() != null;
    boolean hasDef = req.definition() != null;
    if (hasTpl && hasDef) {
      throw ApiException.badRequest("请勿同时指定 templateId 与 definition");
    }
    if (!hasTpl && !hasDef) {
      throw ApiException.badRequest("须指定 templateId 或 definition");
    }
    if (hasTpl) {
      ReportTemplate tpl = reportTemplateMapper.selectByPrimaryKey(req.templateId());
      if (tpl == null || !tpl.getHouseholdId().equals(hid)) {
        throw ApiException.forbidden("报表模板不存在或无权访问");
      }
      try {
        ReportDefinitionPayload base =
            objectMapper.readValue(tpl.getDefinition(), ReportDefinitionPayload.class);
        return base.mergeExtraFilters(req.extraFilters());
      } catch (Exception e) {
        throw ApiException.badRequest("模板 definition 解析失败");
      }
    }
    return req.definition().mergeExtraFilters(req.extraFilters());
  }

  private static Map<String, Object> mapRow(ResultSet rs) throws SQLException {
    ResultSetMetaData md = rs.getMetaData();
    Map<String, Object> row = new LinkedHashMap<>();
    for (int c = 1; c <= md.getColumnCount(); c++) {
      row.put(md.getColumnLabel(c), rs.getObject(c));
    }
    return row;
  }

  private record LookupBundle(
      Map<Long, String> ledgerNames,
      Map<Long, String> categoryNames,
      Map<Long, String> fundNames,
      Map<Long, String> tagNames) {}
}
