package com.duan.metadata;

import lombok.Data;

@Data
public class ColumnMetadata {
    private String columnName;
    private String dataType;
    private boolean autoIncrement;
    private Object defaultValue;
    private String computeExpression;  // 计算列表达式
    private boolean hasDefaultValue;
    private boolean isComputed;        // 是否是计算列
}
