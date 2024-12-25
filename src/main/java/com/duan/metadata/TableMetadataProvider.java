package com.duan.metadata;

import java.util.Map;

public interface TableMetadataProvider {
    /**
     * 获取表的所有列信息，包括默认值、自增等属性
     */
    Map<String, ColumnMetadata> getTableMetadata(String tableName);

    /**
     * 获取表的完整数据（包括默认值、计算值等）
     */
    Map<String, Object> getCompleteRowData(String tableName, Map<String, Object> whereConditions);
}
