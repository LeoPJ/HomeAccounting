package com.homeaccounting.transaction;

import com.homeaccounting.api.dto.ApiResponse;
import com.homeaccounting.api.dto.TransactionPayload;
import com.homeaccounting.api.dto.TransactionWriteBody;
import com.homeaccounting.auth.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

  private final TransactionService transactionService;

  public TransactionController(TransactionService transactionService) {
    this.transactionService = transactionService;
  }

  @GetMapping
  public ApiResponse<List<TransactionPayload>> list(
      HttpServletRequest request,
      @RequestParam(required = false) Long ledgerId,
      @RequestParam(required = false) Long fundAccountId,
      @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          LocalDateTime occurredFrom,
      @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          LocalDateTime occurredTo,
      @RequestParam(required = false, defaultValue = "50") int limit) {
    long uid = CurrentUser.requireUserId(request);
    return ApiResponse.ok(
        transactionService.list(uid, ledgerId, fundAccountId, occurredFrom, occurredTo, limit));
  }

  @GetMapping("/{id}")
  public ApiResponse<TransactionPayload> get(HttpServletRequest request, @PathVariable long id) {
    long uid = CurrentUser.requireUserId(request);
    return ApiResponse.ok(transactionService.get(uid, id));
  }

  @PostMapping
  public ApiResponse<TransactionPayload> create(
      HttpServletRequest request, @Valid @RequestBody TransactionWriteBody body) {
    long uid = CurrentUser.requireUserId(request);
    return ApiResponse.ok(transactionService.create(uid, body));
  }

  @PutMapping("/{id}")
  public ApiResponse<TransactionPayload> update(
      HttpServletRequest request,
      @PathVariable long id,
      @Valid @RequestBody TransactionWriteBody body) {
    long uid = CurrentUser.requireUserId(request);
    return ApiResponse.ok(transactionService.update(uid, id, body));
  }

  @DeleteMapping("/{id}")
  public ApiResponse<Void> delete(HttpServletRequest request, @PathVariable long id) {
    long uid = CurrentUser.requireUserId(request);
    transactionService.delete(uid, id);
    return ApiResponse.ok();
  }
}
