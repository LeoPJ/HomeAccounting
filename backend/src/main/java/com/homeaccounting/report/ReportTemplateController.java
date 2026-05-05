package com.homeaccounting.report;

import com.homeaccounting.api.dto.ApiResponse;
import com.homeaccounting.api.dto.ReportTemplateCreateRequest;
import com.homeaccounting.api.dto.ReportTemplateResponse;
import com.homeaccounting.api.dto.ReportTemplateUpdateRequest;
import com.homeaccounting.auth.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/report-templates")
public class ReportTemplateController {

  private final ReportTemplateService reportTemplateService;

  public ReportTemplateController(ReportTemplateService reportTemplateService) {
    this.reportTemplateService = reportTemplateService;
  }

  @GetMapping
  public ApiResponse<List<ReportTemplateResponse>> list(HttpServletRequest request) {
    long uid = CurrentUser.requireUserId(request);
    return ApiResponse.ok(reportTemplateService.list(uid));
  }

  @GetMapping("/{id}")
  public ApiResponse<ReportTemplateResponse> get(HttpServletRequest request, @PathVariable long id) {
    long uid = CurrentUser.requireUserId(request);
    return ApiResponse.ok(reportTemplateService.get(uid, id));
  }

  @PostMapping
  public ApiResponse<ReportTemplateResponse> create(
      HttpServletRequest request, @Valid @RequestBody ReportTemplateCreateRequest body) {
    long uid = CurrentUser.requireUserId(request);
    return ApiResponse.ok(reportTemplateService.create(uid, body));
  }

  @PutMapping("/{id}")
  public ApiResponse<ReportTemplateResponse> update(
      HttpServletRequest request,
      @PathVariable long id,
      @Valid @RequestBody ReportTemplateUpdateRequest body) {
    long uid = CurrentUser.requireUserId(request);
    return ApiResponse.ok(reportTemplateService.update(uid, id, body));
  }

  @DeleteMapping("/{id}")
  public ApiResponse<Void> delete(HttpServletRequest request, @PathVariable long id) {
    long uid = CurrentUser.requireUserId(request);
    reportTemplateService.delete(uid, id);
    return ApiResponse.ok();
  }
}
