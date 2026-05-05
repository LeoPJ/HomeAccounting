package com.homeaccounting.ledger;

import com.homeaccounting.api.dto.ApiResponse;
import com.homeaccounting.api.dto.NameBody;
import com.homeaccounting.auth.CurrentUser;
import com.homeaccounting.entity.Ledger;
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
@RequestMapping("/api/v1/ledgers")
public class LedgerController {

  private final LedgerService ledgerService;

  public LedgerController(LedgerService ledgerService) {
    this.ledgerService = ledgerService;
  }

  @GetMapping
  public ApiResponse<List<Ledger>> list(HttpServletRequest request) {
    long uid = CurrentUser.requireUserId(request);
    return ApiResponse.ok(ledgerService.list(uid));
  }

  @GetMapping("/{id}")
  public ApiResponse<Ledger> get(HttpServletRequest request, @PathVariable long id) {
    long uid = CurrentUser.requireUserId(request);
    return ApiResponse.ok(ledgerService.get(uid, id));
  }

  @PostMapping
  public ApiResponse<Ledger> create(
      HttpServletRequest request, @Valid @RequestBody NameBody body) {
    long uid = CurrentUser.requireUserId(request);
    return ApiResponse.ok(ledgerService.create(uid, body.name()));
  }

  @PutMapping("/{id}")
  public ApiResponse<Ledger> update(
      HttpServletRequest request,
      @PathVariable long id,
      @Valid @RequestBody NameBody body) {
    long uid = CurrentUser.requireUserId(request);
    return ApiResponse.ok(ledgerService.update(uid, id, body.name()));
  }

  @DeleteMapping("/{id}")
  public ApiResponse<Void> delete(HttpServletRequest request, @PathVariable long id) {
    long uid = CurrentUser.requireUserId(request);
    ledgerService.delete(uid, id);
    return ApiResponse.ok();
  }

}
