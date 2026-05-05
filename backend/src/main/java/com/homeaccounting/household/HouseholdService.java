package com.homeaccounting.household;

import com.homeaccounting.api.ApiException;
import com.homeaccounting.entity.Household;
import com.homeaccounting.entity.HouseholdExample;
import com.homeaccounting.entity.HouseholdMember;
import com.homeaccounting.entity.HouseholdMemberExample;
import com.homeaccounting.mapper.HouseholdMapper;
import com.homeaccounting.mapper.HouseholdMemberMapper;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HouseholdService {

  private static final String ROLE_OWNER = "OWNER";
  private static final String ROLE_MEMBER = "MEMBER";
  private static final String CHARSET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  private static final int INVITE_LEN = 8;

  private final SecureRandom random = new SecureRandom();

  private final HouseholdMapper householdMapper;
  private final HouseholdMemberMapper householdMemberMapper;

  public HouseholdService(
      HouseholdMapper householdMapper, HouseholdMemberMapper householdMemberMapper) {
    this.householdMapper = householdMapper;
    this.householdMemberMapper = householdMemberMapper;
  }

  /** 当前用户是否已加入家庭及详情 */
  public Map<String, Object> getMe(long userId) {
    HouseholdMember member = findMembership(userId);
    if (member == null) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("joined", false);
      return m;
    }
    Household h = householdMapper.selectByPrimaryKey(member.getHouseholdId());
    if (h == null) {
      throw ApiException.badRequest("家庭数据不存在");
    }
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("joined", true);
    m.put("householdId", h.getId());
    m.put("name", h.getName());
    m.put("inviteCode", h.getInviteCode());
    m.put("role", member.getRole());
    return m;
  }

  @Transactional
  public Map<String, Object> createHousehold(long userId, String name) {
    if (findMembership(userId) != null) {
      throw ApiException.badRequest("已在家庭中，无法重复创建");
    }
    Household h = new Household();
    h.setName(name.trim());
    h.setInviteCode(generateUniqueInviteCode());
    h.setCreatedAt(LocalDateTime.now());
    householdMapper.insertSelective(h);

    HouseholdMember row = new HouseholdMember();
    row.setHouseholdId(h.getId());
    row.setUserId(userId);
    row.setRole(ROLE_OWNER);
    row.setJoinedAt(LocalDateTime.now());
    householdMemberMapper.insertSelective(row);

    return Map.of(
        "joined", true,
        "householdId", h.getId(),
        "name", h.getName(),
        "inviteCode", h.getInviteCode(),
        "role", ROLE_OWNER);
  }

  @Transactional
  public Map<String, Object> joinHousehold(long userId, String inviteCodeRaw) {
    if (findMembership(userId) != null) {
      throw ApiException.badRequest("已在家庭中，无法重复加入");
    }
    String code = normalizeInviteCode(inviteCodeRaw);
    Household h = findHouseholdByInviteCode(code);
    if (h == null) {
      throw ApiException.badRequest("邀请码无效或已失效");
    }
    HouseholdMember row = new HouseholdMember();
    row.setHouseholdId(h.getId());
    row.setUserId(userId);
    row.setRole(ROLE_MEMBER);
    row.setJoinedAt(LocalDateTime.now());
    householdMemberMapper.insertSelective(row);

    return Map.of(
        "joined", true,
        "householdId", h.getId(),
        "name", h.getName(),
        "inviteCode", h.getInviteCode(),
        "role", ROLE_MEMBER);
  }

  private HouseholdMember findMembership(long userId) {
    HouseholdMemberExample ex = new HouseholdMemberExample();
    ex.createCriteria().andUserIdEqualTo(userId);
    List<HouseholdMember> list = householdMemberMapper.selectByExample(ex);
    return list.isEmpty() ? null : list.get(0);
  }

  private Household findHouseholdByInviteCode(String code) {
    HouseholdExample ex = new HouseholdExample();
    ex.createCriteria().andInviteCodeEqualTo(code);
    List<Household> list = householdMapper.selectByExample(ex);
    return list.isEmpty() ? null : list.get(0);
  }

  private String normalizeInviteCode(String raw) {
    if (raw == null) {
      return "";
    }
    return raw.trim().toUpperCase();
  }

  private String generateUniqueInviteCode() {
    for (int attempt = 0; attempt < 50; attempt++) {
      String candidate = randomInviteCode();
      HouseholdExample ex = new HouseholdExample();
      ex.createCriteria().andInviteCodeEqualTo(candidate);
      if (householdMapper.countByExample(ex) == 0) {
        return candidate;
      }
    }
    throw ApiException.badRequest("生成邀请码失败，请重试");
  }

  private String randomInviteCode() {
    StringBuilder sb = new StringBuilder(INVITE_LEN);
    for (int i = 0; i < INVITE_LEN; i++) {
      sb.append(CHARSET.charAt(random.nextInt(CHARSET.length())));
    }
    return sb.toString();
  }
}
