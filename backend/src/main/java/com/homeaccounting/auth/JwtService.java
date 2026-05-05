package com.homeaccounting.auth;

import com.homeaccounting.api.ApiException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

  private final AuthProperties props;
  private SecretKey signingKey;

  public JwtService(AuthProperties props) {
    this.props = props;
  }

  @PostConstruct
  void initSigningKey() {
    if (props.getJwtSecret() == null || props.getJwtSecret().isBlank()) {
      throw new IllegalStateException(
          "未配置 JWT_SECRET（至少 32 字节）。"
              + " 本地可：① 设置环境变量 JWT_SECRET；或 ② backend 目录执行"
              + " mvn spring-boot:run -Plocal-dev（PowerShell 勿手写 -Dspring-boot...，易被拆参）；"
              + " 或 $env:SPRING_PROFILES_ACTIVE=\"dev\"; mvn spring-boot:run");
    }
    byte[] bytes = props.getJwtSecret().getBytes(StandardCharsets.UTF_8);
    if (bytes.length < 32) {
      throw new IllegalStateException("JWT_SECRET 长度至少 32 字节（256 bit）");
    }
    signingKey = Keys.hmacShaKeyFor(bytes);
  }

  public String createToken(long userId) {
    Instant now = Instant.now();
    Instant exp = now.plus(props.getJwtExpiration());
    return Jwts.builder()
        .subject(Long.toString(userId))
        .issuedAt(Date.from(now))
        .expiration(Date.from(exp))
        .signWith(signingKey)
        .compact();
  }

  public long parseUserId(String token) {
    try {
      Claims claims =
          Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
      return Long.parseLong(claims.getSubject());
    } catch (Exception e) {
      throw ApiException.unauthorized("登录已失效，请重新登录");
    }
  }
}
