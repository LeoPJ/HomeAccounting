package com.homeaccounting.category;

import com.homeaccounting.api.ApiException;
import com.homeaccounting.entity.Category;
import com.homeaccounting.entity.CategoryExample;
import com.homeaccounting.household.HouseholdScopeService;
import com.homeaccounting.mapper.CategoryMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CategoryService {

  public static final String TYPE_EXPENSE = "EXPENSE";
  public static final String TYPE_INCOME = "INCOME";

  private final CategoryMapper categoryMapper;
  private final HouseholdScopeService householdScopeService;

  public CategoryService(
      CategoryMapper categoryMapper, HouseholdScopeService householdScopeService) {
    this.categoryMapper = categoryMapper;
    this.householdScopeService = householdScopeService;
  }

  public List<Category> list(long userId, String typeFilter) {
    long hid = householdScopeService.requireHouseholdId(userId);
    CategoryExample ex = new CategoryExample();
    CategoryExample.Criteria c = ex.createCriteria().andHouseholdIdEqualTo(hid);
    if (StringUtils.hasText(typeFilter)) {
      validateType(typeFilter.trim());
      c.andTypeEqualTo(typeFilter.trim());
    }
    ex.setOrderByClause("sort_order ASC, id ASC");
    return categoryMapper.selectByExample(ex);
  }

  public Category get(long userId, long categoryId) {
    long hid = householdScopeService.requireHouseholdId(userId);
    Category row = categoryMapper.selectByPrimaryKey(categoryId);
    if (row == null || !row.getHouseholdId().equals(hid)) {
      throw ApiException.forbidden("分类不存在或无权访问");
    }
    return row;
  }

  @Transactional
  public Category create(long userId, String type, String name, Integer sortOrder, Boolean enabled) {
    long hid = householdScopeService.requireHouseholdId(userId);
    validateType(type);
    Category row = new Category();
    row.setHouseholdId(hid);
    row.setType(type.trim());
    row.setName(name.trim());
    row.setSortOrder(sortOrder == null ? 0 : sortOrder);
    row.setEnabled(enabled == null || enabled);
    LocalDateTime now = LocalDateTime.now();
    row.setCreatedAt(now);
    row.setUpdatedAt(now);
    try {
      categoryMapper.insertSelective(row);
    } catch (org.springframework.dao.DataIntegrityViolationException e) {
      throw ApiException.badRequest("同类下已有同名分类");
    }
    return row;
  }

  @Transactional
  public Category update(
      long userId,
      long categoryId,
      String name,
      Integer sortOrder,
      Boolean enabled) {
    get(userId, categoryId);
    Category patch = new Category();
    patch.setId(categoryId);
    if (name != null) {
      patch.setName(name.trim());
    }
    if (sortOrder != null) {
      patch.setSortOrder(sortOrder);
    }
    if (enabled != null) {
      patch.setEnabled(enabled);
    }
    patch.setUpdatedAt(LocalDateTime.now());
    try {
      categoryMapper.updateByPrimaryKeySelective(patch);
    } catch (org.springframework.dao.DataIntegrityViolationException e) {
      throw ApiException.badRequest("同类下已有同名分类");
    }
    return categoryMapper.selectByPrimaryKey(categoryId);
  }

  @Transactional
  public void delete(long userId, long categoryId) {
    get(userId, categoryId);
    categoryMapper.deleteByPrimaryKey(categoryId);
  }

  private static void validateType(String type) {
    if (!TYPE_EXPENSE.equals(type) && !TYPE_INCOME.equals(type)) {
      throw ApiException.badRequest("type 须为 EXPENSE 或 INCOME");
    }
  }
}
