package com.homeaccounting.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homeaccounting.api.ApiException;
import com.homeaccounting.api.dto.ReportTemplateCreateRequest;
import com.homeaccounting.api.dto.ReportTemplateResponse;
import com.homeaccounting.api.dto.ReportTemplateUpdateRequest;
import com.homeaccounting.entity.ReportTemplate;
import com.homeaccounting.household.HouseholdScopeService;
import com.homeaccounting.mapper.ReportTemplateMapper;
import com.homeaccounting.report.model.ReportDefinitionPayload;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportTemplateService {

  private final ReportTemplateMapper reportTemplateMapper;
  private final HouseholdScopeService householdScopeService;
  private final ObjectMapper objectMapper;

  public ReportTemplateService(
      ReportTemplateMapper reportTemplateMapper,
      HouseholdScopeService householdScopeService,
      ObjectMapper objectMapper) {
    this.reportTemplateMapper = reportTemplateMapper;
    this.householdScopeService = householdScopeService;
    this.objectMapper = objectMapper;
  }

  public List<ReportTemplateResponse> list(long userId) {
    long hid = householdScopeService.requireHouseholdId(userId);
    return reportTemplateMapper.selectByHouseholdId(hid).stream().map(this::toResponse).toList();
  }

  public ReportTemplateResponse get(long userId, long templateId) {
    ReportTemplate row = requireOwned(userId, templateId);
    return toResponse(row);
  }

  @Transactional
  public ReportTemplateResponse create(long userId, ReportTemplateCreateRequest req) {
    long hid = householdScopeService.requireHouseholdId(userId);
    ReportDefinitionValidator.validate(req.definition());
    ReportTemplate row = new ReportTemplate();
    row.setHouseholdId(hid);
    row.setName(req.name().trim());
    row.setDefinition(writeJson(req.definition()));
    row.setCreatedBy(userId);
    try {
      reportTemplateMapper.insert(row);
    } catch (DataIntegrityViolationException e) {
      throw ApiException.badRequest("同名报表模板已存在");
    }
    return toResponse(reportTemplateMapper.selectByPrimaryKey(row.getId()));
  }

  @Transactional
  public ReportTemplateResponse update(long userId, long templateId, ReportTemplateUpdateRequest req) {
    ReportTemplate row = requireOwned(userId, templateId);
    if ((req.name() == null || req.name().isBlank()) && req.definition() == null) {
      throw ApiException.badRequest("请至少提供 name 或 definition 之一");
    }
    ReportTemplate patch = new ReportTemplate();
    patch.setId(templateId);
    if (req.name() != null && !req.name().isBlank()) {
      patch.setName(req.name().trim());
    }
    if (req.definition() != null) {
      ReportDefinitionValidator.validate(req.definition());
      patch.setDefinition(writeJson(req.definition()));
    }
    try {
      reportTemplateMapper.updateByPrimaryKeySelective(patch);
    } catch (DataIntegrityViolationException e) {
      throw ApiException.badRequest("同名报表模板已存在");
    }
    return toResponse(reportTemplateMapper.selectByPrimaryKey(templateId));
  }

  @Transactional
  public void delete(long userId, long templateId) {
    requireOwned(userId, templateId);
    reportTemplateMapper.deleteByPrimaryKey(templateId);
  }

  private ReportTemplate requireOwned(long userId, long templateId) {
    long hid = householdScopeService.requireHouseholdId(userId);
    ReportTemplate row = reportTemplateMapper.selectByPrimaryKey(templateId);
    if (row == null || !row.getHouseholdId().equals(hid)) {
      throw ApiException.forbidden("报表模板不存在或无权访问");
    }
    return row;
  }

  private String writeJson(ReportDefinitionPayload def) {
    try {
      return objectMapper.writeValueAsString(def);
    } catch (JsonProcessingException e) {
      throw ApiException.badRequest("报表定义无法序列化");
    }
  }

  private ReportTemplateResponse toResponse(ReportTemplate row) {
    JsonNode defNode;
    try {
      defNode = objectMapper.readTree(row.getDefinition());
    } catch (JsonProcessingException e) {
      defNode = objectMapper.createObjectNode();
    }
    return new ReportTemplateResponse(
        row.getId(),
        row.getHouseholdId(),
        row.getName(),
        defNode,
        row.getCreatedBy(),
        row.getCreatedAt(),
        row.getUpdatedAt());
  }
}
