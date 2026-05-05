package com.homeaccounting.api;

import com.homeaccounting.api.dto.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class PingController {

  @GetMapping("/ping")
  public ApiResponse<Map<String, String>> ping() {
    return ApiResponse.ok(Map.of("status", "up"));
  }
}
