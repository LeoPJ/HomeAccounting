package com.homeaccounting.report;

import com.homeaccounting.api.dto.ApiResponse;
import com.homeaccounting.api.dto.ReportExecuteRequest;
import com.homeaccounting.api.dto.ReportRunResponse;
import com.homeaccounting.auth.CurrentUser;
import com.homeaccounting.report.schema.ReportSchemaResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportQueryController {

  private final ReportQueryService reportQueryService;

  public ReportQueryController(ReportQueryService reportQueryService) {
    this.reportQueryService = reportQueryService;
  }

  /** 返回报表 DSL 白名单（与校验/SQL 拼装共用数据源），供小程序或管理端动态表单使用 */
  @GetMapping("/schema")
  public ApiResponse<ReportSchemaResponse> schema(HttpServletRequest request) {
    CurrentUser.requireUserId(request);
    return ApiResponse.ok(ReportSchemaCatalog.buildResponse());
  }

  /** 按模板或内联定义执行聚合查询（仅拼接白名单 SQL + 绑定参数） */
  @PostMapping("/run")
  public ApiResponse<ReportRunResponse> run(
      HttpServletRequest request, @Valid @RequestBody ReportExecuteRequest body) {
    long uid = CurrentUser.requireUserId(request);
    return ApiResponse.ok(reportQueryService.run(uid, body));
  }
}
