package com.duan.service;

import com.duan.config.AuditConfig;
import com.duan.entity.DataAuditLog;
import com.duan.enums.OperationType;
import com.duan.repository.DataAuditLogRepository;
import com.duan.utils.JsonUtils;
import com.duan.utils.SQLInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {
    protected final AuditConfig auditConfig;
    protected final DataAuditLogRepository dataAuditLogRepository;
    protected final JdbcTemplate jdbcTemplate;

    @Async("auditExecutor")
    public void saveAuditLog(SQLInfo sqlInfo) {
        if (!needAudit(sqlInfo)) {
            log.debug("no needAudit");
            return;
        }
        log.debug("needAudit");
        try {
            DataAuditLog log = createAuditLog(sqlInfo);
            dataAuditLogRepository.save(log);
        } catch (Exception e) {
            log.error("Save audit log failed", e);
            // 重试机制
            retrySaveAuditLog(sqlInfo);
        }
    }

    public Map<String, Object> getBeforeData(SQLInfo sqlInfo) {
        String selectSql = String.format("SELECT * FROM %s WHERE %s",
                sqlInfo.getTableName(), sqlInfo.getWhereClause());
        try {
            return jdbcTemplate.queryForMap(selectSql);
        } catch (EmptyResultDataAccessException e) {
            return Collections.emptyMap();
        }
    }

    public Map<String, Object> getAfterData(SQLInfo sqlInfo) {
        if (sqlInfo.getOperationType() == OperationType.INSERT) {
            String selectSql = String.format("SELECT * FROM %s WHERE %s",
                    sqlInfo.getTableName(),
                    buildPrimaryKeyWhere(sqlInfo.getNewData()));
            try {
                return jdbcTemplate.queryForMap(selectSql);
            } catch (EmptyResultDataAccessException e) {
                return Collections.emptyMap();
            }
        }
        return getBeforeData(sqlInfo);
    }

    private boolean needAudit(SQLInfo sqlInfo) {
        if (!auditConfig.isEnabled()) {
            return false;
        }

        String tableName = sqlInfo.getTableName();

        if (tableName.equals("sys_data_audit_log")) {
            return false;
        }

        if (auditConfig.getIncludeTables() != null &&
                !auditConfig.getIncludeTables().isEmpty() &&
                !auditConfig.getIncludeTables().contains(tableName)) {
            return false;
        }

        if (auditConfig.getExcludeTables() != null &&
                !auditConfig.getExcludeTables().isEmpty() &&
                auditConfig.getExcludeTables().contains(tableName)) {
            return false;
        }

        return true;
    }

    private DataAuditLog createAuditLog(SQLInfo sqlInfo) {
        DataAuditLog log = new DataAuditLog();
        log.setTableName(sqlInfo.getTableName());
        log.setOperationType(sqlInfo.getOperationType().toString());
        log.setOperateTime(LocalDateTime.now());
        log.setOperator(getCurrentOperator());

        if (sqlInfo.getOldData() != null) {
            log.setOldValue(JsonUtils.toJson(filterColumns(
                    sqlInfo.getTableName(), sqlInfo.getOldData())));
        }

        if (sqlInfo.getNewData() != null) {
            log.setNewValue(JsonUtils.toJson(filterColumns(
                    sqlInfo.getTableName(), sqlInfo.getNewData())));
        }

        return log;
    }

    private Map<String, Object> filterColumns(String tableName, Map<String, Object> data) {
        if (auditConfig.getIncludeColumns() == null) {
            return data;
        }
        List<String> includeColumns = auditConfig.getIncludeColumns().get(tableName);
        if (includeColumns == null || includeColumns.isEmpty()) {
            return data;
        }

        return data.entrySet().stream()
                .filter(entry -> includeColumns.contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private void retrySaveAuditLog(SQLInfo sqlInfo) {
        int retryCount = 0;
        while (retryCount < auditConfig.getMaxRetries()) {
            try {
                Thread.sleep(1000 * (retryCount + 1));
                DataAuditLog log = createAuditLog(sqlInfo);
                dataAuditLogRepository.save(log);
                return;
            } catch (Exception e) {
                log.error("Retry save audit log failed, attempt: {}", retryCount + 1, e);
                retryCount++;
            }
        }
    }

    private String getCurrentOperator() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            return auth != null ? auth.getName() : "SYSTEM";
        } catch (Exception e) {
            return "SYSTEM";
        }
    }

    private String buildPrimaryKeyWhere(Map<String, Object> data) {
        return data.entrySet().stream()
                .map(e -> String.format("%s = '%s'", e.getKey(), e.getValue()))
                .collect(Collectors.joining(" AND "));
    }
}
