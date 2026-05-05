package com.homeaccounting.fund;

import com.homeaccounting.api.ApiException;
import com.homeaccounting.entity.FundAccount;
import com.homeaccounting.entity.FundAccountExample;
import com.homeaccounting.household.HouseholdScopeService;
import com.homeaccounting.mapper.FundAccountMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FundAccountService {

  private final FundAccountMapper fundAccountMapper;
  private final HouseholdScopeService householdScopeService;

  public FundAccountService(
      FundAccountMapper fundAccountMapper, HouseholdScopeService householdScopeService) {
    this.fundAccountMapper = fundAccountMapper;
    this.householdScopeService = householdScopeService;
  }

  public List<FundAccount> list(long userId) {
    long hid = householdScopeService.requireHouseholdId(userId);
    FundAccountExample ex = new FundAccountExample();
    ex.createCriteria().andHouseholdIdEqualTo(hid);
    ex.setOrderByClause("created_at DESC");
    return fundAccountMapper.selectByExample(ex);
  }

  public FundAccount get(long userId, long accountId) {
    long hid = householdScopeService.requireHouseholdId(userId);
    FundAccount row = fundAccountMapper.selectByPrimaryKey(accountId);
    if (row == null || !row.getHouseholdId().equals(hid)) {
      throw ApiException.forbidden("资金账号不存在或无权访问");
    }
    return row;
  }

  @Transactional
  public FundAccount create(long userId, String name, BigDecimal initialBalance) {
    long hid = householdScopeService.requireHouseholdId(userId);
    BigDecimal bal =
        initialBalance == null ? BigDecimal.ZERO.setScale(4) : initialBalance.setScale(4);
    FundAccount row = new FundAccount();
    row.setHouseholdId(hid);
    row.setName(name.trim());
    row.setBalance(bal);
    row.setVersion(0L);
    LocalDateTime now = LocalDateTime.now();
    row.setCreatedAt(now);
    row.setUpdatedAt(now);
    fundAccountMapper.insertSelective(row);
    return row;
  }

  /** 仅改名；余额仅能通过记账流水变更 */
  @Transactional
  public FundAccount updateName(long userId, long accountId, String name) {
    get(userId, accountId);
    FundAccount patch = new FundAccount();
    patch.setId(accountId);
    patch.setName(name.trim());
    patch.setUpdatedAt(LocalDateTime.now());
    fundAccountMapper.updateByPrimaryKeySelective(patch);
    return fundAccountMapper.selectByPrimaryKey(accountId);
  }

  @Transactional
  public void delete(long userId, long accountId) {
    get(userId, accountId);
    fundAccountMapper.deleteByPrimaryKey(accountId);
  }
}
