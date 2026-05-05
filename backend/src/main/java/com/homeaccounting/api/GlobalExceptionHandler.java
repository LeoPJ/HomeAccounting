package com.homeaccounting.api;

import com.homeaccounting.api.dto.ApiResponse;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  private final Environment environment;

  public GlobalExceptionHandler(Environment environment) {
    this.environment = environment;
  }

  private boolean isDevProfileActive() {
    return Arrays.stream(environment.getActiveProfiles())
        .anyMatch(p -> "dev".equalsIgnoreCase(p));
  }

  private ResponseEntity<ApiResponse<Void>> databaseFailureResponse(Throwable root) {
    String hint =
        "数据库访问失败。请确认：MySQL 已启动；已创建库 home_accounting；DB_USER/DB_PASSWORD 与实例一致；Flyway 已迁移。";
    if (isDevProfileActive()) {
      hint += " 详情: " + root.getClass().getSimpleName() + ": " + root.getMessage();
    }
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(ApiResponse.fail(503, hint));
  }

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ApiResponse<Void>> handleApi(ApiException ex) {
    return ResponseEntity.status(ex.getStatus()).body(ApiResponse.fail(ex.getCode(), ex.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
    String msg =
        ex.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(err -> err.getField() + ": " + err.getDefaultMessage())
            .orElse("参数校验失败");
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.fail(400, msg));
  }

  @ExceptionHandler(DataAccessException.class)
  public ResponseEntity<ApiResponse<Void>> handleDataAccess(DataAccessException ex) {
    log.error("Data access error", ex);
    return databaseFailureResponse(NestedExceptionUtils.getMostSpecificCause(ex));
  }

  /**
   * {@code @Transactional} 失败时常包装为 TransactionException，根因仍是 JDBC / SQL（未必 instanceof
   * DataAccessException）。
   */
  @ExceptionHandler(TransactionException.class)
  public ResponseEntity<ApiResponse<Void>> handleTransaction(TransactionException ex) {
    Throwable root = NestedExceptionUtils.getMostSpecificCause(ex);
    if (root instanceof DataAccessException) {
      log.error("Data access error", ex);
      return databaseFailureResponse(NestedExceptionUtils.getMostSpecificCause(ex));
    }
    if (root instanceof java.sql.SQLException) {
      log.error("Transaction failed (SQL)", ex);
      return databaseFailureResponse(root);
    }
    log.error("Transaction failed", ex);
    String message = "服务器内部错误";
    if (isDevProfileActive()) {
      message =
          ex.getClass().getSimpleName()
              + ": "
              + ex.getMessage()
              + " → "
              + root.getClass().getSimpleName()
              + ": "
              + root.getMessage();
    }
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ApiResponse.fail(500, message));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
    log.error("Unhandled error", ex);
    String message = "服务器内部错误";
    if (isDevProfileActive()) {
      Throwable root = NestedExceptionUtils.getMostSpecificCause(ex);
      message =
          ex.getClass().getSimpleName()
              + ": "
              + ex.getMessage()
              + (root != ex
                  ? " → "
                      + root.getClass().getSimpleName()
                      + ": "
                      + root.getMessage()
                  : "");
    }
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ApiResponse.fail(500, message));
  }
}
