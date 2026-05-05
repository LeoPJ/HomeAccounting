package com.homeaccounting.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.homeaccounting.api.ApiException;
import com.homeaccounting.api.dto.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 50)
public class JwtAuthFilter extends OncePerRequestFilter {

  private static final List<String> PUBLIC_PATHS =
      List.of("/api/v1/ping", "/api/v1/auth/**");

  private static final UrlPathHelper PATH_HELPER = new UrlPathHelper();

  private final JwtService jwtService;
  private final ObjectMapper objectMapper;
  private final AntPathMatcher pathMatcher = new AntPathMatcher();

  public JwtAuthFilter(JwtService jwtService, ObjectMapper objectMapper) {
    this.jwtService = jwtService;
    this.objectMapper = objectMapper;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    // 去掉 context-path，避免部署在子路径下时误判为「非 /api」从而跳过 JWT，导致 Controller 里 userId 为 null 拆箱 NPE
    String path = PATH_HELPER.getPathWithinApplication(request);

    if (!path.startsWith("/api/")) {
      filterChain.doFilter(request, response);
      return;
    }

    if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
      filterChain.doFilter(request, response);
      return;
    }

    if (isPublic(path)) {
      filterChain.doFilter(request, response);
      return;
    }

    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
      writeUnauthorized(response, "缺少 Authorization: Bearer token");
      return;
    }

    String token = header.substring(7).trim();
    if (token.isEmpty()) {
      writeUnauthorized(response, "token 为空");
      return;
    }

    try {
      long userId = jwtService.parseUserId(token);
      request.setAttribute(CurrentUser.REQUEST_ATTR_USER_ID, userId);
    } catch (ApiException ex) {
      writeApiException(response, ex);
      return;
    }

    filterChain.doFilter(request, response);
  }

  private boolean isPublic(String path) {
    return PUBLIC_PATHS.stream().anyMatch(p -> pathMatcher.match(p, path));
  }

  private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
    writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, ApiResponse.fail(401, message));
  }

  private void writeApiException(HttpServletResponse response, ApiException ex) throws IOException {
    writeJson(response, ex.getStatus().value(), ApiResponse.fail(ex.getCode(), ex.getMessage()));
  }

  private void writeJson(HttpServletResponse response, int httpStatus, ApiResponse<?> body)
      throws IOException {
    response.setStatus(httpStatus);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(response.getOutputStream(), body);
  }
}
