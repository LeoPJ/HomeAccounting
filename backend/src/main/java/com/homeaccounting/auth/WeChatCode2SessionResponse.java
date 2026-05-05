package com.homeaccounting.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

public class WeChatCode2SessionResponse {

  private Integer errcode;

  private String errmsg;

  private String openid;

  private String unionid;

  @JsonProperty("session_key")
  private String sessionKey;

  public Integer getErrcode() {
    return errcode;
  }

  public void setErrcode(Integer errcode) {
    this.errcode = errcode;
  }

  public String getErrmsg() {
    return errmsg;
  }

  public void setErrmsg(String errmsg) {
    this.errmsg = errmsg;
  }

  public String getOpenid() {
    return openid;
  }

  public void setOpenid(String openid) {
    this.openid = openid;
  }

  public String getUnionid() {
    return unionid;
  }

  public void setUnionid(String unionid) {
    this.unionid = unionid;
  }

  public String getSessionKey() {
    return sessionKey;
  }

  public void setSessionKey(String sessionKey) {
    this.sessionKey = sessionKey;
  }
}
