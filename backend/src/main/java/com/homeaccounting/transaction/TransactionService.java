package com.homeaccounting.transaction;

import com.homeaccounting.api.ApiException;
import com.homeaccounting.api.dto.TransactionPayload;
import com.homeaccounting.api.dto.TransactionWriteBody;
import com.homeaccounting.category.CategoryService;
import com.homeaccounting.entity.AcctTransaction;
import com.homeaccounting.entity.Category;
import com.homeaccounting.entity.FundAccount;
import com.homeaccounting.entity.TransactionTag;
import com.homeaccounting.entity.TransactionTagExample;
import com.homeaccounting.fund.FundAccountService;
import com.homeaccounting.household.HouseholdScopeService;
import com.homeaccounting.ledger.LedgerService;
import com.homeaccounting.mapper.AcctTransactionMapper;
import com.homeaccounting.mapper.FundAccountMapper;
import com.homeaccounting.mapper.TransactionTagMapper;
import com.homeaccounting.tag.TagService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionService {

  private final AcctTransactionMapper acctTransactionMapper;
  private final TransactionTagMapper transactionTagMapper;
  private final FundAccountMapper fundAccountMapper;
  private final HouseholdScopeService householdScopeService;
  private final LedgerService ledgerService;
  private final CategoryService categoryService;
  private final FundAccountService fundAccountService;
  private final TagService tagService;

  public TransactionService(
      AcctTransactionMapper acctTransactionMapper,
      TransactionTagMapper transactionTagMapper,
      FundAccountMapper fundAccountMapper,
      HouseholdScopeService householdScopeService,
      LedgerService ledgerService,
      CategoryService categoryService,
      FundAccountService fundAccountService,
      TagService tagService) {
    this.acctTransactionMapper = acctTransactionMapper;
    this.transactionTagMapper = transactionTagMapper;
    this.fundAccountMapper = fundAccountMapper;
    this.householdScopeService = householdScopeService;
    this.ledgerService = ledgerService;
    this.categoryService = categoryService;
    this.fundAccountService = fundAccountService;
    this.tagService = tagService;
  }

  public TransactionPayload get(long userId, long transactionId) {
    AcctTransaction row = requireOwned(userId, transactionId);
    return toPayload(row, loadTagIds(transactionId));
  }

  public List<TransactionPayload> list(
      long userId,
      Long ledgerId,
      Long fundAccountId,
      LocalDateTime occurredFrom,
      LocalDateTime occurredTo,
      int limit) {
    long hid = householdScopeService.requireHouseholdId(userId);
    if (ledgerId != null) {
      ledgerService.get(userId, ledgerId);
    }
    if (fundAccountId != null) {
      fundAccountService.get(userId, fundAccountId);
    }
    int lim = Math.min(Math.max(limit, 1), 200);
    List<AcctTransaction> rows =
        acctTransactionMapper.selectHouseholdLedgerLimited(
            hid, ledgerId, fundAccountId, occurredFrom, occurredTo, lim);
    if (rows.isEmpty()) {
      return List.of();
    }
    List<Long> ids = rows.stream().map(AcctTransaction::getId).toList();
    Map<Long, List<Long>> tagMap = loadTagIdsBatch(ids);
    return rows.stream()
        .map(r -> toPayload(r, tagMap.getOrDefault(r.getId(), List.of())))
        .toList();
  }

  @Transactional
  public TransactionPayload create(long userId, TransactionWriteBody body) {
    long hid = householdScopeService.requireHouseholdId(userId);
    validateWrite(userId, body);

    BigDecimal amt = body.amount().setScale(4, RoundingMode.HALF_UP);
    LocalDateTime now = LocalDateTime.now();
    AcctTransaction row = new AcctTransaction();
    row.setHouseholdId(hid);
    row.setLedgerId(body.ledgerId());
    row.setFundAccountId(body.fundAccountId());
    row.setCategoryId(body.categoryId());
    row.setType(body.type());
    row.setAmount(amt);
    row.setOccurredAt(body.occurredAt());
    row.setNote(body.note());
    row.setCreatedBy(userId);
    row.setCreatedAt(now);
    row.setUpdatedAt(now);
    acctTransactionMapper.insertSelective(row);
    long txId = row.getId();
    if (body.fundAccountId() != null) {
      adjustFundBalance(hid, body.fundAccountId(), balanceDelta(body.type(), amt));
    }
    replaceTags(txId, distinctTagIds(body.tagIds()));
    return toPayload(acctTransactionMapper.selectByPrimaryKey(txId), loadTagIds(txId));
  }

  @Transactional
  public TransactionPayload update(long userId, long transactionId, TransactionWriteBody body) {
    AcctTransaction old = requireOwned(userId, transactionId);
    long hid = old.getHouseholdId();
    validateWrite(userId, body);

    BigDecimal amt = body.amount().setScale(4, RoundingMode.HALF_UP);
    reverseFundEffect(old);

    AcctTransaction merged = new AcctTransaction();
    merged.setId(transactionId);
    merged.setHouseholdId(hid);
    merged.setLedgerId(body.ledgerId());
    merged.setFundAccountId(body.fundAccountId());
    merged.setCategoryId(body.categoryId());
    merged.setType(body.type());
    merged.setAmount(amt);
    merged.setOccurredAt(body.occurredAt());
    merged.setNote(body.note());
    merged.setCreatedBy(old.getCreatedBy());
    merged.setCreatedAt(old.getCreatedAt());
    merged.setUpdatedAt(LocalDateTime.now());
    acctTransactionMapper.updateByPrimaryKey(merged);

    if (body.fundAccountId() != null) {
      adjustFundBalance(hid, body.fundAccountId(), balanceDelta(body.type(), amt));
    }
    replaceTags(transactionId, distinctTagIds(body.tagIds()));
    return toPayload(
        acctTransactionMapper.selectByPrimaryKey(transactionId), loadTagIds(transactionId));
  }

  @Transactional
  public void delete(long userId, long transactionId) {
    AcctTransaction old = requireOwned(userId, transactionId);
    reverseFundEffect(old);
    acctTransactionMapper.deleteByPrimaryKey(transactionId);
  }

  private AcctTransaction requireOwned(long userId, long txId) {
    long hid = householdScopeService.requireHouseholdId(userId);
    AcctTransaction row = acctTransactionMapper.selectByPrimaryKey(txId);
    if (row == null || !row.getHouseholdId().equals(hid)) {
      throw ApiException.forbidden("流水不存在或无权访问");
    }
    return row;
  }

  private void validateWrite(long userId, TransactionWriteBody body) {
    validateType(body.type());
    ledgerService.get(userId, body.ledgerId());
    Category cat = categoryService.get(userId, body.categoryId());
    if (!cat.getType().equals(body.type())) {
      throw ApiException.badRequest("分类类型与流水类型不一致");
    }
    if (body.fundAccountId() != null) {
      fundAccountService.get(userId, body.fundAccountId());
    }
    for (Long tid : body.tagIds()) {
      tagService.get(userId, tid);
    }
  }

  private static void validateType(String type) {
    if (!CategoryService.TYPE_EXPENSE.equals(type) && !CategoryService.TYPE_INCOME.equals(type)) {
      throw ApiException.badRequest("type 须为 EXPENSE 或 INCOME");
    }
  }

  private static BigDecimal balanceDelta(String type, BigDecimal amount) {
    if (CategoryService.TYPE_EXPENSE.equals(type)) {
      return amount.negate();
    }
    return amount;
  }

  private void reverseFundEffect(AcctTransaction old) {
    if (old.getFundAccountId() == null) {
      return;
    }
    adjustFundBalance(
        old.getHouseholdId(),
        old.getFundAccountId(),
        balanceDelta(old.getType(), old.getAmount()).negate());
  }

  private void adjustFundBalance(long householdId, long fundAccountId, BigDecimal delta) {
    FundAccount acc = fundAccountMapper.selectByPrimaryKey(fundAccountId);
    if (acc == null || !acc.getHouseholdId().equals(householdId)) {
      throw ApiException.forbidden("资金账号不存在或无权访问");
    }
    BigDecimal newBal = acc.getBalance().add(delta).setScale(4, RoundingMode.HALF_UP);
    if (newBal.compareTo(BigDecimal.ZERO) < 0) {
      throw ApiException.badRequest("账户余额不足");
    }
    LocalDateTime now = LocalDateTime.now();
    int n =
        fundAccountMapper.updateBalanceOptimistic(
            fundAccountId,
            householdId,
            acc.getVersion(),
            newBal,
            acc.getVersion() + 1,
            now);
    if (n == 0) {
      throw ApiException.conflict("资金账户余额已被其他操作更新，请重试");
    }
  }

  private void replaceTags(long transactionId, List<Long> tagIds) {
    TransactionTagExample del = new TransactionTagExample();
    del.createCriteria().andTransactionIdEqualTo(transactionId);
    transactionTagMapper.deleteByExample(del);
    for (Long tagId : tagIds) {
      TransactionTag link = new TransactionTag();
      link.setTransactionId(transactionId);
      link.setTagId(tagId);
      transactionTagMapper.insert(link);
    }
  }

  private static List<Long> distinctTagIds(List<Long> raw) {
    return raw.stream().distinct().collect(Collectors.toList());
  }

  private List<Long> loadTagIds(long transactionId) {
    TransactionTagExample ex = new TransactionTagExample();
    ex.createCriteria().andTransactionIdEqualTo(transactionId);
    return transactionTagMapper.selectByExample(ex).stream()
        .map(TransactionTag::getTagId)
        .sorted()
        .toList();
  }

  private Map<Long, List<Long>> loadTagIdsBatch(List<Long> transactionIds) {
    TransactionTagExample ex = new TransactionTagExample();
    ex.createCriteria().andTransactionIdIn(transactionIds);
    Map<Long, List<Long>> map = new HashMap<>();
    for (TransactionTag tt : transactionTagMapper.selectByExample(ex)) {
      map.computeIfAbsent(tt.getTransactionId(), k -> new ArrayList<>()).add(tt.getTagId());
    }
    map.values().forEach(list -> list.sort(Comparator.naturalOrder()));
    return map;
  }

  private static TransactionPayload toPayload(AcctTransaction row, List<Long> tagIds) {
    return new TransactionPayload(
        row.getId(),
        row.getHouseholdId(),
        row.getLedgerId(),
        row.getFundAccountId(),
        row.getCategoryId(),
        row.getType(),
        row.getAmount(),
        row.getOccurredAt(),
        row.getNote(),
        row.getCreatedBy(),
        row.getCreatedAt(),
        row.getUpdatedAt(),
        tagIds);
  }
}
