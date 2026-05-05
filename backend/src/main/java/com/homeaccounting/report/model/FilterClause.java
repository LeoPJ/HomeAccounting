package com.homeaccounting.report.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** 筛选条件：字段与运算符均在服务端白名单内；参数走预编译绑定 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FilterClause(String field, String op, List<Object> params) {

  public FilterClause {
    params = params != null ? List.copyOf(params) : List.of();
  }
}
