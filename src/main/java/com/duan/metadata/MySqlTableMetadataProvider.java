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
        // 首先尝试从缓存获取
        Cache cache = cacheManager.getCache("tableMetadata");
        if (cache != null) {
            Map<String, ColumnMetadata> cachedMetadata = cache.get(tableName, Map.class);
            if (cachedMetadata != null) {
                return cachedMetadata;
            }
        }

        Map<String, ColumnMetadata> metadata = new HashMap<>();

        // 查询表的列信息
        String sql = """
                SELECT 
                    COLUMN_NAME, 
                    DATA_TYPE,
                    COLUMN_DEFAULT,
                    EXTRA,
                    GENERATION_EXPRESSION
                FROM INFORMATION_SCHEMA.COLUMNS 
                WHERE TABLE_NAME = ? AND TABLE_SCHEMA = DATABASE()
                """;

        jdbcTemplate.query(sql, rs -> {
            ColumnMetadata columnMeta = new ColumnMetadata();
            columnMeta.setColumnName(rs.getString("COLUMN_NAME"));
            columnMeta.setDataType(rs.getString("DATA_TYPE"));
            columnMeta.setDefaultValue(rs.getString("COLUMN_DEFAULT"));
            columnMeta.setHasDefaultValue(rs.getString("COLUMN_DEFAULT") != null);
            columnMeta.setAutoIncrement(rs.getString("EXTRA").contains("auto_increment"));

            String generateExpression = rs.getString("GENERATION_EXPRESSION");
            if (StringUtils.hasText(generateExpression)) {
                columnMeta.setComputed(true);
                columnMeta.setComputeExpression(generateExpression);
            }

            metadata.put(columnMeta.getColumnName(), columnMeta);
        }, tableName);

        // 存入缓存
        if (cache != null) {
            cache.put(tableName, metadata);
        }

        return metadata;
    }

    @Override
    public Map<String, Object> getCompleteRowData(String tableName, Map<String, Object> whereConditions) {
        // 构建查询SQL
        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(tableName).append(" WHERE ");
        List<Object> params = new ArrayList<>();

        whereConditions.forEach((key, value) -> {
            if (!params.isEmpty()) {
                sql.append(" AND ");
            }
            sql.append(key).append(" = ?");
            params.add(value);
        });

        try {
            return jdbcTemplate.queryForMap(sql.toString(), params.toArray());
        } catch (EmptyResultDataAccessException e) {
            return Collections.emptyMap();
        }
    }
}
