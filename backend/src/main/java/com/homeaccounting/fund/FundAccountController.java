package com.homeaccounting.fund;

import com.homeaccounting.api.dto.ApiResponse;
import com.homeaccounting.api.dto.NameBody;
import com.homeaccounting.auth.CurrentUser;
import com.homeaccounting.entity.FundAccount;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
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
@RequestMapping("/api/v1/fund-accounts")
public class FundAccountController {

  private final FundAccountService fundAccountService;

  public FundAccountController(FundAccountService fundAccountService) {
    this.fundAccountService = fundAccountService;
  }

  @GetMapping
  public ApiResponse<List<FundAccount>> list(HttpServletRequest request) {
    long uid = CurrentUser.requireUserId(request);
    return ApiResponse.ok(fundAccountService.list(uid));
  }

  @GetMapping("/{id}")
  public ApiResponse<FundAccount> get(HttpServletRequest request, @PathVariable long id) {
    long uid = CurrentUser.requireUserId(request);
    return ApiResponse.ok(fundAccountService.get(uid, id));
  }

  @PostMapping
  public ApiResponse<FundAccount> create(
      HttpServletRequest request, @Valid @RequestBody CreateFundAccountBody body) {
    long uid = CurrentUser.requireUserId(request);
    return ApiResponse.ok(
        fundAccountService.create(uid, body.name(), body.initialBalance()));
  }

  @PutMapping("/{id}")
  public ApiResponse<FundAccount> update(
      HttpServletRequest request,
      @PathVariable long id,
      @Valid @RequestBody NameBody body) {
    long uid = CurrentUser.requireUserId(request);
    return ApiResponse.ok(fundAccountService.updateName(uid, id, body.name()));
  }

  @DeleteMapping("/{id}")
  public ApiResponse<Void> delete(HttpServletRequest request, @PathVariable long id) {
    long uid = CurrentUser.requireUserId(request);
    fundAccountService.delete(uid, id);
    return ApiResponse.ok();
  }

  /** local record - ledger NameBody reuse would cross-package; duplicate minimal */
  public record CreateFundAccountBody(
      @NotBlank @Size(max = 64) String name, BigDecimal initialBalance) {}
}
