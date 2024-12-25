package com.duan.utils;

import com.duan.enums.OperationType;
import lombok.Data;

import java.util.Map;

@Data
public class SQLInfo {
    private String tableName;
    private OperationType operationType;
    private String whereClause;
    private Map<String, Object> newData;
    private Map<String, Object> oldData;
}
