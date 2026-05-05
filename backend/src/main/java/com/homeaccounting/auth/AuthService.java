package com.homeaccounting.auth;

import com.homeaccounting.entity.HouseholdMember;
import com.homeaccounting.entity.HouseholdMemberExample;
import com.homeaccounting.entity.User;
import com.homeaccounting.entity.UserExample;
import com.homeaccounting.api.ApiException;
import com.homeaccounting.mapper.HouseholdMemberMapper;
import com.homeaccounting.mapper.UserMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

  private static final Pattern DEV_LABEL = Pattern.compile("[a-zA-Z0-9_-]{1,64}");

  private final UserMapper userMapper;
  private final HouseholdMemberMapper householdMemberMapper;
  private final WeChatMiniProgramClient weChatClient;
  private final JwtService jwtService;
  private final AuthProperties authProperties;

  public AuthService(
      UserMapper userMapper,
      HouseholdMemberMapper householdMemberMapper,
      WeChatMiniProgramClient weChatClient,
      JwtService jwtService,
      AuthProperties authProperties) {
    this.userMapper = userMapper;
    this.householdMemberMapper = householdMemberMapper;
    this.weChatClient = weChatClient;
    this.jwtService = jwtService;
    this.authProperties = authProperties;
  }

  @Transactional
  public LoginResult loginWithWechatCode(String jsCode) {
    WeChatCode2SessionResponse wx = weChatClient.code2Session(jsCode);

    User user = findUserByOpenid(wx.getOpenid());
    if (user == null) {
      user = new User();
      user.setWechatOpenid(wx.getOpenid());
      user.setWechatUnionid(wx.getUnionid());
      user.setCreatedAt(LocalDateTime.now());
      user.setUpdatedAt(LocalDateTime.now());
      userMapper.insertSelective(user);
    } else if (wx.getUnionid() != null && !wx.getUnionid().equals(user.getWechatUnionid())) {
      User patch = new User();
      patch.setId(user.getId());
      patch.setWechatUnionid(wx.getUnionid());
      patch.setUpdatedAt(LocalDateTime.now());
      userMapper.updateByPrimaryKeySelective(patch);
    }

    Long householdId = resolveHouseholdId(user.getId());
    String token = jwtService.createToken(user.getId());
    return new LoginResult(token, user.getId(), householdId);
  }

  /**
   * 本地调试：无需微信。用户在 users.wechat_openid 中为 {@code dev|标签}，与真实 openid 区分。
   */
  @Transactional
  public LoginResult loginDev(String providedSecret, String label) {
    if (!authProperties.isDevLoginEnabled()) {
      throw ApiException.forbidden("未开启本地测试登录（DEV_LOGIN_ENABLED）");
    }
    String expected = authProperties.getDevLoginSecret();
    if (expected == null || expected.isBlank()) {
      throw ApiException.badRequest("未配置 DEV_LOGIN_SECRET");
    }
    if (!expected.equals(providedSecret)) {
      throw ApiException.unauthorized("secret 不正确");
    }
    String trimmed = label == null ? "" : label.trim();
    if (trimmed.isEmpty()) {
      trimmed = "default";
    }
    if (!DEV_LABEL.matcher(trimmed).matches()) {
      throw ApiException.badRequest("label 仅允许 1～64 位字母数字下划线横线");
    }
    String syntheticOpenid = "dev|" + trimmed;

    User user = findUserByOpenid(syntheticOpenid);
    if (user == null) {
      user = new User();
      user.setWechatOpenid(syntheticOpenid);
      user.setCreatedAt(LocalDateTime.now());
      user.setUpdatedAt(LocalDateTime.now());
      userMapper.insertSelective(user);
    }

    Long householdId = resolveHouseholdId(user.getId());
    String token = jwtService.createToken(user.getId());
    return new LoginResult(token, user.getId(), householdId);
  }

  private User findUserByOpenid(String openid) {
    UserExample ex = new UserExample();
    ex.createCriteria().andWechatOpenidEqualTo(openid);
    List<User> list = userMapper.selectByExample(ex);
    return list.isEmpty() ? null : list.get(0);
  }

  private Long resolveHouseholdId(long userId) {
    HouseholdMemberExample ex = new HouseholdMemberExample();
    ex.createCriteria().andUserIdEqualTo(userId);
    List<HouseholdMember> list = householdMemberMapper.selectByExample(ex);
    return list.isEmpty() ? null : list.get(0).getHouseholdId();
  }

  public record LoginResult(String token, Long userId, Long householdId) {
    public boolean needsHousehold() {
      return householdId == null;
    }
  }
}
