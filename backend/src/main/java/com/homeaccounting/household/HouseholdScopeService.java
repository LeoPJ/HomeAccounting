package com.homeaccounting.household;

import com.homeaccounting.api.ApiException;
import com.homeaccounting.entity.HouseholdMember;
import com.homeaccounting.entity.HouseholdMemberExample;
import com.homeaccounting.mapper.HouseholdMemberMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class HouseholdScopeService {

  private final HouseholdMemberMapper householdMemberMapper;

  public HouseholdScopeService(HouseholdMemberMapper householdMemberMapper) {
    this.householdMemberMapper = householdMemberMapper;
  }

  /** 当前用户所在家庭 ID；未加入任何家庭则 403 */
  public long requireHouseholdId(long userId) {
    HouseholdMemberExample ex = new HouseholdMemberExample();
    ex.createCriteria().andUserIdEqualTo(userId);
    List<HouseholdMember> list = householdMemberMapper.selectByExample(ex);
    if (list.isEmpty()) {
      throw ApiException.forbidden("请先创建或加入家庭");
    }
    return list.get(0).getHouseholdId();
  }
}
