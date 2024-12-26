package com.duan.aspect;

import com.duan.config.AuditConfig;
import com.duan.enums.OperationType;
import com.duan.metadata.ColumnMetadata;
import com.duan.metadata.TableMetadataProvider;
import com.duan.service.TransactionAwareEnhancedAuditService;
import com.duan.utils.SQLInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class JpaAuditAspect {
    private final TransactionAwareEnhancedAuditService transactionAwareEnhancedAuditService;
    private final AuditConfig auditConfig;
    private final TableMetadataProvider metadataProvider;

    @Around("execution(* javax.persistence.EntityManager.persist(..)) || " +
            "execution(* javax.persistence.EntityManager.merge(..)) || " +
            "execution(* javax.persistence.EntityManager.remove(..))")
    public Object aroundEntityManager(ProceedingJoinPoint point) throws Throwable {
        if (!auditConfig.isEnabled()) {
            return point.proceed();
        }

        Object[] args = point.getArgs();
        if (args.length == 0) {
            return point.proceed();
        }

        Object entity = args[0];
        String methodName = point.getSignature().getName();
        String tableName = getTableName(entity);

        Map<String, ColumnMetadata> tableMetadata = metadataProvider.getTableMetadata(tableName);

        // 获取实体信息
        SQLInfo sqlInfo = new SQLInfo();
        sqlInfo.setTableName(tableName);

        // 设置操作类型
        switch (methodName) {
            case "persist":
                sqlInfo.setOperationType(OperationType.INSERT);
                sqlInfo.setNewData(getEntityData(entity));
                break;
            case "merge":
                sqlInfo.setOperationType(OperationType.UPDATE);
                Map<String, Object> primaryKeyData = getPrimaryKeyData(entity, tableMetadata);
                if (!primaryKeyData.isEmpty()) {
                    sqlInfo.setOldData(metadataProvider.getCompleteRowData(tableName, primaryKeyData));
                }
                break;
            case "remove":
                sqlInfo.setOperationType(OperationType.DELETE);
                primaryKeyData = getPrimaryKeyData(entity, tableMetadata);
                if (!primaryKeyData.isEmpty()) {
                    sqlInfo.setOldData(metadataProvider.getCompleteRowData(tableName, primaryKeyData));
                }
                break;
        }

        // 执行原始操作
        Object result = point.proceed();

        // 获取新数据
        if (methodName.equals("persist") || methodName.equals("merge")) {
            Map<String, Object> newPrimaryKeyData = getPrimaryKeyData(entity, tableMetadata);
            if (!newPrimaryKeyData.isEmpty()) {
                sqlInfo.setNewData(metadataProvider.getCompleteRowData(tableName, newPrimaryKeyData));
            }
        }

        try {
            // 记录审计日志
            transactionAwareEnhancedAuditService.saveAuditLog(sqlInfo);
        } catch (Exception e) {
            log.error("Audit failed", e);
        }

        return result;
    }

    private String getTableName(Object entity) {
        Table table = entity.getClass().getAnnotation(Table.class);
        if (table != null && StringUtils.hasText(table.name())) {
            return table.name();
        }
        return entity.getClass().getSimpleName().toLowerCase();
    }

    private Map<String, Object> getEntityData(Object entity) {
        Map<String, Object> data = new HashMap<>();
        for (Field field : entity.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) ||
                    Modifier.isTransient(field.getModifiers())) {
                continue;
            }

            field.setAccessible(true);
            try {
                Column column = field.getAnnotation(Column.class);
                String columnName = (column != null && StringUtils.hasText(column.name()))
                        ? column.name()
                        : field.getName();

                data.put(columnName, field.get(entity));
            } catch (Exception e) {
                log.error("Get field value failed", e);
            }
        }
        return data;
    }

    private Map<String, Object> getPrimaryKeyData(Object entity, Map<String, ColumnMetadata> metadata) {
        Map<String, Object> primaryKeyData = new HashMap<>();

        for (Field field : entity.getClass().getDeclaredFields()) {
            if (field.isAnnotationPresent(Id.class)) {
                field.setAccessible(true);
                try {
                    Column column = field.getAnnotation(Column.class);
                    String columnName = (column != null && StringUtils.hasText(column.name()))
                            ? column.name()
                            : field.getName();

                    Object value = field.get(entity);
                    if (value != null) {
                        primaryKeyData.put(columnName, value);
                    }
                } catch (Exception e) {
                    log.error("Get primary key value failed", e);
                }
            }
        }

        return primaryKeyData;
    }

}
