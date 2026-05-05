package com.homeaccounting.report.schema;

import java.util.List;

/**
 * @param valueType datetime | enum | long | tag
 * @param enumValues 仅 valueType=enum 时有值
 */
public record FilterFieldSchema(
    String id,
    String label,
    String valueType,
    List<String> ops,
    List<String> enumValues) {

  public FilterFieldSchema(String id, String label, String valueType, List<String> ops) {
    this(id, label, valueType, ops, List.of());
  }
}
