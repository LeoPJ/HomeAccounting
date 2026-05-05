package com.homeaccounting.api;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

  /** 逗号分隔；默认 * 方便本地与小程序调试，上线建议改为固定域名列表 */
  @Value("${app.cors.allowed-origin-patterns:*}")
  private String allowedOriginPatterns;

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    List<String> patterns =
        List.of(allowedOriginPatterns.split(",")).stream().map(String::trim).toList();
    registry
        .addMapping("/api/**")
        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        .allowedHeaders("*")
        // JWT 放 Header 时不依赖 Cookie；若今后要用 Cookie 会话，勿配 *，改为明确域名并改为 allowCredentials(true)
        .allowCredentials(false)
        .allowedOriginPatterns(patterns.toArray(String[]::new));
  }
}
