package com.duan.utils;

import com.duan.enums.OperationType;
import com.duan.metadata.ColumnMetadata;
import com.duan.metadata.TableMetadataProvider;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class EnhancedSQLParser extends SQLParser {
    private final TableMetadataProvider metadataProvider;

    public EnhancedSQLParser(TableMetadataProvider metadataProvider) {
        this.metadataProvider = metadataProvider;
    }

    @Override
    public SQLInfo parseSql(String sql) {
        SQLInfo sqlInfo = super.parseSql(sql);
        if (sqlInfo == null) {
            return null;
        }

        // 获取表的元数据信息
        Map<String, ColumnMetadata> tableMetadata =
                metadataProvider.getTableMetadata(sqlInfo.getTableName());

        // 补充默认值和计算列信息
        if (sqlInfo.getOperationType() == OperationType.INSERT) {
            enhanceInsertData(sqlInfo, tableMetadata);
        } else if (sqlInfo.getOperationType() == OperationType.UPDATE) {
            enhanceUpdateData(sqlInfo, tableMetadata);
        }

        return sqlInfo;
    }

    private void enhanceInsertData(SQLInfo sqlInfo, Map<String, ColumnMetadata> tableMetadata) {
        Map<String, Object> enhancedData = new HashMap<>(sqlInfo.getNewData());

        // 补充默认值
        tableMetadata.forEach((columnName, metadata) -> {
            if (!enhancedData.containsKey(columnName)) {
                if (metadata.isHasDefaultValue()) {
                    enhancedData.put(columnName, metadata.getDefaultValue());
                } else if (metadata.isAutoIncrement()) {
                    // 自增列将在插入后获取
                    enhancedData.put(columnName, null);
                } else if (metadata.isComputed()) {
                    // 计算列将在插入后获取
                    enhancedData.put(columnName, null);
                }
            }
        });

        sqlInfo.setNewData(enhancedData);
    }

    private void enhanceUpdateData(SQLInfo sqlInfo, Map<String, ColumnMetadata> tableMetadata) {
        Map<String, Object> enhancedData = new HashMap<>(sqlInfo.getNewData());

        // 处理计算列
        tableMetadata.forEach((columnName, metadata) -> {
            if (metadata.isComputed()) {
                enhancedData.put(columnName, null);
            }
        });

        sqlInfo.setNewData(enhancedData);
    }
}
