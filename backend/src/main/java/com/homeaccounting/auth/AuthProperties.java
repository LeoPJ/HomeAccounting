package com.homeaccounting.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

  /** HS256 密钥，至少 32 字节；可用 openssl rand -base64 32 生成 */
  private String jwtSecret = "";

  private Duration jwtExpiration = Duration.ofDays(7);

  private String wechatAppId = "";

  private String wechatAppSecret = "";

  /** 仅本地调试：为 true 且配置 dev-login-secret 后可用 /auth/dev/login */
  private boolean devLoginEnabled = false;

  /** 与请求体 secret 一致方可登录；勿在线上开启 */
  private String devLoginSecret = "";

  public String getJwtSecret() {
    return jwtSecret;
  }

  public void setJwtSecret(String jwtSecret) {
    this.jwtSecret = jwtSecret;
  }

  public Duration getJwtExpiration() {
    return jwtExpiration;
  }

  public void setJwtExpiration(Duration jwtExpiration) {
    this.jwtExpiration = jwtExpiration;
  }

  public String getWechatAppId() {
    return wechatAppId;
  }

  public void setWechatAppId(String wechatAppId) {
    this.wechatAppId = wechatAppId;
  }

  public String getWechatAppSecret() {
    return wechatAppSecret;
  }

  public void setWechatAppSecret(String wechatAppSecret) {
    this.wechatAppSecret = wechatAppSecret;
  }

  public boolean isDevLoginEnabled() {
    return devLoginEnabled;
  }

  public void setDevLoginEnabled(boolean devLoginEnabled) {
    this.devLoginEnabled = devLoginEnabled;
  }

  public String getDevLoginSecret() {
    return devLoginSecret;
  }

  public void setDevLoginSecret(String devLoginSecret) {
    this.devLoginSecret = devLoginSecret;
  }
}
