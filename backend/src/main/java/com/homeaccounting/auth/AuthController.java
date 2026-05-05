package com.homeaccounting.auth;

import com.homeaccounting.api.dto.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/wechat/login")
  public ApiResponse<Map<String, Object>> wechatLogin(@Valid @RequestBody WechatLoginBody body) {
    AuthService.LoginResult r = authService.loginWithWechatCode(body.code());
    return ApiResponse.ok(tokenPayload(r));
  }

  /** 仅本地：需 DEV_LOGIN_ENABLED=true 且请求 secret 与 DEV_LOGIN_SECRET 一致 */
  @PostMapping("/dev/login")
  public ApiResponse<Map<String, Object>> devLogin(@Valid @RequestBody DevLoginBody body) {
    AuthService.LoginResult r = authService.loginDev(body.secret(), body.label());
    Map<String, Object> data = tokenPayload(r);
    data.put("loginMode", "dev");
    return ApiResponse.ok(data);
  }

  private static Map<String, Object> tokenPayload(AuthService.LoginResult r) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("token", r.token());
    data.put("tokenType", "Bearer");
    data.put("userId", r.userId());
    data.put("householdId", r.householdId());
    data.put("needsHousehold", r.needsHousehold());
    return data;
  }

  public record WechatLoginBody(@NotBlank String code) {}

  public record DevLoginBody(@NotBlank String secret, String label) {}
}
