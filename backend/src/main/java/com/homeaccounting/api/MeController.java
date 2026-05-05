package com.homeaccounting.api;

import com.homeaccounting.api.dto.ApiResponse;
import com.homeaccounting.auth.CurrentUser;
import com.homeaccounting.entity.User;
import com.homeaccounting.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 用于验证 JWT：请求头需携带 Authorization: Bearer {token} */
@RestController
@RequestMapping("/api/v1")
public class MeController {

  private final UserMapper userMapper;

  public MeController(UserMapper userMapper) {
    this.userMapper = userMapper;
  }

  @GetMapping("/me")
  public ApiResponse<Map<String, Object>> me(HttpServletRequest request) {
    long uid = CurrentUser.requireUserId(request);
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("userId", uid);
    User u = userMapper.selectByPrimaryKey(uid);
    if (u != null) {
      if (u.getNickname() != null && !u.getNickname().isBlank()) {
        m.put("nickname", u.getNickname().trim());
      }
      if (u.getAvatarUrl() != null && !u.getAvatarUrl().isBlank()) {
        m.put("avatarUrl", u.getAvatarUrl().trim());
      }
    }
    return ApiResponse.ok(m);
  }
}
