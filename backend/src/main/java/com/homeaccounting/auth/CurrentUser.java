package com.homeaccounting.auth;

import com.homeaccounting.api.ApiException;
import jakarta.servlet.http.HttpServletRequest;

public final class CurrentUser {

  public static final String REQUEST_ATTR_USER_ID = "loginUserId";

  private CurrentUser() {}

  public static Long userId(HttpServletRequest request) {
    Object v = request.getAttribute(REQUEST_ATTR_USER_ID);
    if (v == null) {
      return null;
    }
    if (v instanceof Long id) {
      return id;
    }
    if (v instanceof Number n) {
      return n.longValue();
    }
    return null;
  }

  /** 业务接口应使用此方法，避免 Long 拆箱成 long 时空指针 */
  public static long requireUserId(HttpServletRequest request) {
    Long id = userId(request);
    if (id == null) {
      throw ApiException.unauthorized("未登录");
    }
    return id;
  }
}
