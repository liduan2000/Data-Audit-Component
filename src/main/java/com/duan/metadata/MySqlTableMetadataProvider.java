package com.duan.metadata;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class MySqlTableMetadataProvider implements TableMetadataProvider {
    private final JdbcTemplate jdbcTemplate;
    private final CacheManager cacheManager;

    @Override
    public Map<String, ColumnMetadata> getTableMetadata(String tableName) {
        // 检查表名是否为空
        if (!StringUtils.hasText(tableName)) {
            log.error("Table name is null or empty");
            return new HashMap<>();
        }

        // 检查缓存管理器是否可用
        if (cacheManager == null) {
            log.warn("CacheManager is not initialized, skipping cache");
            return queryTableMetadata(tableName);
        }

        // 获取缓存实例
        Cache cache = cacheManager.getCache("tableMetadata");
        if (cache == null) {
            log.warn("Cache 'tableMetadata' not found, skipping cache");
            return queryTableMetadata(tableName);
        }

        try {
            // 尝试从缓存获取
            Cache.ValueWrapper valueWrapper = cache.get(tableName);
            if (valueWrapper != null) {
                @SuppressWarnings("unchecked")
                Map<String, ColumnMetadata> cachedMetadata = (Map<String, ColumnMetadata>) valueWrapper.get();
                if (cachedMetadata != null) {
                    return cachedMetadata;
                }
            }
        } catch (Exception e) {
            log.error("Failed to get metadata from cache for table: " + tableName, e);
        }

        // 如果缓存中没有，查询数据库
        Map<String, ColumnMetadata> metadata = queryTableMetadata(tableName);

        // 尝试存入缓存
        try {
            if (!metadata.isEmpty()) {
                cache.put(tableName, metadata);
            }
        } catch (Exception e) {
            log.error("Failed to put metadata to cache for table: " + tableName, e);
        }

        return metadata;
    }

    private Map<String, ColumnMetadata> queryTableMetadata(String tableName) {
        Map<String, ColumnMetadata> metadata = new HashMap<>();

        try {
            String sql = """
                    SELECT 
                        COLUMN_NAME, 
                        DATA_TYPE,
                        COLUMN_DEFAULT,
                        EXTRA,
                        GENERATION_EXPRESSION,
                        IS_NULLABLE
                    FROM INFORMATION_SCHEMA.COLUMNS 
                    WHERE TABLE_NAME = ? AND TABLE_SCHEMA = DATABASE()
                    """;

            jdbcTemplate.query(sql, rs -> {
                ColumnMetadata columnMeta = new ColumnMetadata();
                columnMeta.setColumnName(rs.getString("COLUMN_NAME"));
                columnMeta.setDataType(rs.getString("DATA_TYPE"));

                String defaultValue = rs.getString("COLUMN_DEFAULT");
                columnMeta.setDefaultValue(defaultValue);
                columnMeta.setHasDefaultValue(defaultValue != null);

                String extra = rs.getString("EXTRA");
                columnMeta.setAutoIncrement(extra != null && extra.contains("auto_increment"));

                String generateExpression = rs.getString("GENERATION_EXPRESSION");
                if (StringUtils.hasText(generateExpression)) {
                    columnMeta.setComputed(true);
                    columnMeta.setComputeExpression(generateExpression);
                }

                metadata.put(columnMeta.getColumnName(), columnMeta);
            }, tableName);
        } catch (Exception e) {
            log.error("Failed to query metadata for table: " + tableName, e);
        }

        return metadata;
    }

    @Override
    public Map<String, Object> getCompleteRowData(String tableName, Map<String, Object> whereConditions) {
        if (!StringUtils.hasText(tableName) || whereConditions == null || whereConditions.isEmpty()) {
            return Collections.emptyMap();
        }

        try {
            StringBuilder sql = new StringBuilder("SELECT * FROM ").append(tableName).append(" WHERE ");
            List<Object> params = new ArrayList<>();

            boolean first = true;
            for (Map.Entry<String, Object> entry : whereConditions.entrySet()) {
                if (!first) {
                    sql.append(" AND ");
                }
                sql.append(entry.getKey()).append(" = ?");
                params.add(entry.getValue());
                first = false;
            }

            return jdbcTemplate.queryForMap(sql.toString(), params.toArray());
        } catch (EmptyResultDataAccessException e) {
            return Collections.emptyMap();
        } catch (Exception e) {
            log.error("Failed to get complete row data for table: " + tableName, e);
            return Collections.emptyMap();
        }
    }
}
