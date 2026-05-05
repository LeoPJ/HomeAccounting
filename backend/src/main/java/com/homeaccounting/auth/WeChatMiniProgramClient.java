package com.homeaccounting.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.homeaccounting.api.ApiException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class WeChatMiniProgramClient {

  private final RestClient restClient;
  private final AuthProperties props;
  private final ObjectMapper objectMapper;

  public WeChatMiniProgramClient(
      RestClient.Builder restClientBuilder, AuthProperties props, ObjectMapper objectMapper) {
    this.restClient = restClientBuilder.build();
    this.props = props;
    this.objectMapper = objectMapper;
  }

  public WeChatCode2SessionResponse code2Session(String jsCode) {
    if (props.getWechatAppId().isBlank() || props.getWechatAppSecret().isBlank()) {
      throw ApiException.badRequest("服务端未配置微信小程序 AppId/AppSecret");
    }
    String uri =
        UriComponentsBuilder.fromUriString("https://api.weixin.qq.com/sns/jscode2session")
            .queryParam("appid", props.getWechatAppId())
            .queryParam("secret", props.getWechatAppSecret())
            .queryParam("js_code", jsCode)
            .queryParam("grant_type", "authorization_code")
            .encode()
            .build()
            .toUriString();

    // 微信接口返回 JSON，但 Content-Type 常为 text/plain，RestClient 无法直接反序列化为对象
    String raw =
        restClient.get().uri(uri).retrieve().body(String.class);

    if (raw == null || raw.isBlank()) {
      throw ApiException.upstream("微信登录接口无响应");
    }

    WeChatCode2SessionResponse body;
    try {
      body = objectMapper.readValue(raw, WeChatCode2SessionResponse.class);
    } catch (Exception e) {
      throw ApiException.upstream("解析微信响应失败: " + raw);
    }

    if (body == null) {
      throw ApiException.upstream("微信登录接口无响应");
    }
    if (body.getErrcode() != null && body.getErrcode() != 0) {
      throw ApiException.badRequest(
          "微信登录失败: " + body.getErrcode() + " " + body.getErrmsg());
    }
    if (body.getOpenid() == null || body.getOpenid().isBlank()) {
      throw ApiException.upstream("微信未返回 openid");
    }
    return body;
  }
}
