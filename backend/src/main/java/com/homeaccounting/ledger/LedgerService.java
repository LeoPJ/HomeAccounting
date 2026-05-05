package com.homeaccounting.ledger;

import com.homeaccounting.api.ApiException;
import com.homeaccounting.entity.Ledger;
import com.homeaccounting.entity.LedgerExample;
import com.homeaccounting.household.HouseholdScopeService;
import com.homeaccounting.mapper.LedgerMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerService {

  private final LedgerMapper ledgerMapper;
  private final HouseholdScopeService householdScopeService;

  public LedgerService(LedgerMapper ledgerMapper, HouseholdScopeService householdScopeService) {
    this.ledgerMapper = ledgerMapper;
    this.householdScopeService = householdScopeService;
  }

  public List<Ledger> list(long userId) {
    long hid = householdScopeService.requireHouseholdId(userId);
    LedgerExample ex = new LedgerExample();
    ex.createCriteria().andHouseholdIdEqualTo(hid);
    ex.setOrderByClause("created_at DESC");
    return ledgerMapper.selectByExample(ex);
  }

  public Ledger get(long userId, long ledgerId) {
    long hid = householdScopeService.requireHouseholdId(userId);
    Ledger row = ledgerMapper.selectByPrimaryKey(ledgerId);
    if (row == null || !row.getHouseholdId().equals(hid)) {
      throw ApiException.forbidden("账本不存在或无权访问");
    }
    return row;
  }

  @Transactional
  public Ledger create(long userId, String name) {
    long hid = householdScopeService.requireHouseholdId(userId);
    Ledger row = new Ledger();
    row.setHouseholdId(hid);
    row.setName(name.trim());
    LocalDateTime now = LocalDateTime.now();
    row.setCreatedAt(now);
    row.setUpdatedAt(now);
    ledgerMapper.insertSelective(row);
    return row;
  }

  @Transactional
  public Ledger update(long userId, long ledgerId, String name) {
    Ledger row = get(userId, ledgerId);
    Ledger patch = new Ledger();
    patch.setId(row.getId());
    patch.setName(name.trim());
    patch.setUpdatedAt(LocalDateTime.now());
    ledgerMapper.updateByPrimaryKeySelective(patch);
    return ledgerMapper.selectByPrimaryKey(ledgerId);
  }

  @Transactional
  public void delete(long userId, long ledgerId) {
    get(userId, ledgerId);
    ledgerMapper.deleteByPrimaryKey(ledgerId);
  }
}
