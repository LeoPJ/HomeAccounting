package com.homeaccounting.tag;

import com.homeaccounting.api.ApiException;
import com.homeaccounting.entity.Tag;
import com.homeaccounting.entity.TagExample;
import com.homeaccounting.household.HouseholdScopeService;
import com.homeaccounting.mapper.TagMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TagService {

  private final TagMapper tagMapper;
  private final HouseholdScopeService householdScopeService;

  public TagService(TagMapper tagMapper, HouseholdScopeService householdScopeService) {
    this.tagMapper = tagMapper;
    this.householdScopeService = householdScopeService;
  }

  public List<Tag> list(long userId) {
    long hid = householdScopeService.requireHouseholdId(userId);
    TagExample ex = new TagExample();
    ex.createCriteria().andHouseholdIdEqualTo(hid);
    ex.setOrderByClause("created_at DESC");
    return tagMapper.selectByExample(ex);
  }

  public Tag get(long userId, long tagId) {
    long hid = householdScopeService.requireHouseholdId(userId);
    Tag row = tagMapper.selectByPrimaryKey(tagId);
    if (row == null || !row.getHouseholdId().equals(hid)) {
      throw ApiException.forbidden("标签不存在或无权访问");
    }
    return row;
  }

  @Transactional
  public Tag create(long userId, String name) {
    long hid = householdScopeService.requireHouseholdId(userId);
    Tag row = new Tag();
    row.setHouseholdId(hid);
    row.setName(name.trim());
    LocalDateTime now = LocalDateTime.now();
    row.setCreatedAt(now);
    row.setUpdatedAt(now);
    try {
      tagMapper.insertSelective(row);
    } catch (DataIntegrityViolationException e) {
      throw ApiException.badRequest("已有同名标签");
    }
    return row;
  }

  @Transactional
  public Tag update(long userId, long tagId, String name) {
    get(userId, tagId);
    Tag patch = new Tag();
    patch.setId(tagId);
    patch.setName(name.trim());
    patch.setUpdatedAt(LocalDateTime.now());
    try {
      tagMapper.updateByPrimaryKeySelective(patch);
    } catch (DataIntegrityViolationException e) {
      throw ApiException.badRequest("已有同名标签");
    }
    return tagMapper.selectByPrimaryKey(tagId);
  }

  @Transactional
  public void delete(long userId, long tagId) {
    get(userId, tagId);
    tagMapper.deleteByPrimaryKey(tagId);
  }
}
