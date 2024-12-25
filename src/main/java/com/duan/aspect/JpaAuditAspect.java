package com.duan.aspect;

import com.duan.config.AuditConfig;
import com.duan.enums.OperationType;
import com.duan.service.EnhancedAuditService;
import com.duan.utils.SQLInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

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
    private final EnhancedAuditService enhancedAuditService;
    private final AuditConfig auditConfig;

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

        // 获取实体信息
        SQLInfo sqlInfo = new SQLInfo();
        sqlInfo.setTableName(getTableName(entity));

        // 设置操作类型
        switch (methodName) {
            case "persist":
                sqlInfo.setOperationType(OperationType.INSERT);
                break;
            case "merge":
                sqlInfo.setOperationType(OperationType.UPDATE);
                sqlInfo.setOldData(getEntityData(entity));
                break;
            case "remove":
                sqlInfo.setOperationType(OperationType.DELETE);
                sqlInfo.setOldData(getEntityData(entity));
                break;
        }

        // 执行原始操作
        Object result = point.proceed();

        // 获取新数据
        if (methodName.equals("persist") || methodName.equals("merge")) {
            sqlInfo.setNewData(getEntityData(entity));
        }

        try {
            // 记录审计日志
            enhancedAuditService.saveAuditLog(sqlInfo);
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
            field.setAccessible(true);
            try {
                // 排除静态字段和瞬时字段
                if (!Modifier.isStatic(field.getModifiers()) &&
                        !Modifier.isTransient(field.getModifiers())) {
                    data.put(field.getName(), field.get(entity));
                }
            } catch (Exception e) {
                log.error("Get field value failed", e);
            }
        }
        return data;
    }

}
