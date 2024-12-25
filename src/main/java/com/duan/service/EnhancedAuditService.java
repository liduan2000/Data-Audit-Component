package com.duan.service;

import com.duan.config.AuditConfig;
import com.duan.enums.OperationType;
import com.duan.metadata.ColumnMetadata;
import com.duan.metadata.TableMetadataProvider;
import com.duan.repository.DataAuditLogRepository;
import com.duan.utils.SQLInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class EnhancedAuditService extends AuditService {
    private final TableMetadataProvider metadataProvider;

    public EnhancedAuditService(AuditConfig auditConfig, DataAuditLogRepository dataAuditLogRepository, JdbcTemplate jdbcTemplate, TableMetadataProvider metadataProvider) {
        super(auditConfig, dataAuditLogRepository, jdbcTemplate);
        this.metadataProvider = metadataProvider;
    }

    @Override
    public Map<String, Object> getAfterData(SQLInfo sqlInfo) {
        // 获取完整的行数据（包括默认值和计算列）
        Map<String, Object> primaryKeyData = extractPrimaryKeyData(sqlInfo);
        if (primaryKeyData.isEmpty()) {
            return Collections.emptyMap();
        }

        return metadataProvider.getCompleteRowData(sqlInfo.getTableName(), primaryKeyData);
    }

    private Map<String, Object> extractPrimaryKeyData(SQLInfo sqlInfo) {
        if (sqlInfo.getOperationType() == OperationType.INSERT) {
            return extractPrimaryKeyFromNewData(sqlInfo);
        } else {
            return extractPrimaryKeyFromWhereClause(sqlInfo);
        }
    }

    private Map<String, Object> extractPrimaryKeyFromNewData(SQLInfo sqlInfo) {
        // 从元数据中获取主键信息
        Map<String, ColumnMetadata> metadata =
                metadataProvider.getTableMetadata(sqlInfo.getTableName());

        Map<String, Object> primaryKeyData = new HashMap<>();
        metadata.forEach((columnName, columnMetadata) -> {
            if (isPrimaryKey(columnMetadata)) {
                Object value = sqlInfo.getNewData().get(columnName);
                if (value != null) {
                    primaryKeyData.put(columnName, value);
                }
            }
        });

        return primaryKeyData;
    }

    private boolean isPrimaryKey(ColumnMetadata metadata) {
        // 实现主键判断逻辑
        // 可以通过查询INFORMATION_SCHEMA.KEY_COLUMN_USAGE获取
        try {
            DatabaseMetaData databaseMetaData = jdbcTemplate.getDataSource().getConnection().getMetaData();
            ResultSet primaryKeys = databaseMetaData.getPrimaryKeys(null, null, metadata.getColumnName());

            while (primaryKeys.next()) {
                String columnName = primaryKeys.getString("COLUMN_NAME");
                if (columnName.equalsIgnoreCase(metadata.getColumnName())) {
                    return true;
                }
            }
        } catch (SQLException e) {
            log.error("Failed to check primary key for column: {}", metadata.getColumnName(), e);
        }
        return false;
    }

    private Map<String, Object> extractPrimaryKeyFromWhereClause(SQLInfo sqlInfo) {
        // 从WHERE子句中提取主键条件
        // 这需要实现SQL解析逻辑
        Map<String, Object> primaryKeyData = new HashMap<>();

        try {
            // 从元数据中获取表主键
            Map<String, ColumnMetadata> metadata = metadataProvider.getTableMetadata(sqlInfo.getTableName());
            String whereClause = sqlInfo.getWhereClause();
            if (whereClause == null || whereClause.isEmpty()) {
                return primaryKeyData;
            }

            // 构造正则表达式匹配 "column = value" 结构
            Pattern pattern = Pattern.compile("(\\w+)\\s*=\\s*'?(\\w+)'?");
            Matcher matcher = pattern.matcher(whereClause);

            while (matcher.find()) {
                String columnName = matcher.group(1);
                String value = matcher.group(2);

                // 检查该列是否为主键
                ColumnMetadata columnMetadata = metadata.get(columnName);
                if (columnMetadata != null && isPrimaryKey(columnMetadata)) {
                    primaryKeyData.put(columnName, value);
                }
            }
        } catch (Exception e) {
            log.error("Failed to extract primary key from WHERE clause: {}", sqlInfo.getWhereClause(), e);
        }

        return primaryKeyData;
    }
}
