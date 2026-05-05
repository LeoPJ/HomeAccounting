package com.homeaccounting.api;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

  private final HttpStatus status;
  private final int code;

  public ApiException(HttpStatus status, int code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  public HttpStatus getStatus() {
    return status;
  }

  public int getCode() {
    return code;
  }

  public static ApiException badRequest(String message) {
    return new ApiException(HttpStatus.BAD_REQUEST, 400, message);
  }

  public static ApiException unauthorized(String message) {
    return new ApiException(HttpStatus.UNAUTHORIZED, 401, message);
  }

  public static ApiException forbidden(String message) {
    return new ApiException(HttpStatus.FORBIDDEN, 403, message);
  }

  /** 乐观锁冲突等 */
  public static ApiException conflict(String message) {
    return new ApiException(HttpStatus.CONFLICT, 409, message);
  }

  public static ApiException upstream(String message) {
    return new ApiException(HttpStatus.BAD_GATEWAY, 502, message);
  }
}
