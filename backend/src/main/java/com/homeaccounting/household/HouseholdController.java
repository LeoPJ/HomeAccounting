package com.homeaccounting.household;

import com.homeaccounting.api.dto.ApiResponse;
import com.homeaccounting.auth.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/households")
public class HouseholdController {

  private final HouseholdService householdService;

  public HouseholdController(HouseholdService householdService) {
    this.householdService = householdService;
  }

  /** 当前登录用户所在家庭；未加入则 joined=false */
  @GetMapping("/me")
  public ApiResponse<Map<String, Object>> me(HttpServletRequest request) {
    long userId = CurrentUser.requireUserId(request);
    return ApiResponse.ok(householdService.getMe(userId));
  }

  /** 创建家庭并成为户主 */
  @PostMapping
  public ApiResponse<Map<String, Object>> create(
      HttpServletRequest request, @Valid @RequestBody CreateHouseholdBody body) {
    long userId = CurrentUser.requireUserId(request);
    return ApiResponse.ok(householdService.createHousehold(userId, body.name()));
  }

  /** 凭邀请码加入家庭 */
  @PostMapping("/join")
  public ApiResponse<Map<String, Object>> join(
      HttpServletRequest request, @Valid @RequestBody JoinHouseholdBody body) {
    long userId = CurrentUser.requireUserId(request);
    return ApiResponse.ok(householdService.joinHousehold(userId, body.inviteCode()));
  }

  public record CreateHouseholdBody(
      @NotBlank @Size(max = 64) String name) {}

  public record JoinHouseholdBody(
      @NotBlank @Size(max = 16) String inviteCode) {}
}
