package com.duan.aspect;

import com.duan.config.AuditConfig;
import com.duan.enums.OperationType;
import com.duan.service.TransactionAwareEnhancedAuditService;
import com.duan.utils.EnhancedSQLParser;
import com.duan.utils.SQLInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class JdbcTemplateAuditAspect {
    private final TransactionAwareEnhancedAuditService transactionAwareEnhancedAuditService;
    private final AuditConfig auditConfig;
    private final EnhancedSQLParser enhancedSQLParser;

    @Around("execution(* org.springframework.jdbc.core.JdbcTemplate.*(String, ..))")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        if (!auditConfig.isEnabled()) {
            return point.proceed();
        }

        Object[] args = point.getArgs();
        String sql = (String) args[0];

        // 解析SQL
        SQLInfo sqlInfo = enhancedSQLParser.parseSql(sql);
        if (sqlInfo == null || sqlInfo.getOperationType() == null) {
            return point.proceed();
        }

        // 如果是更新或删除操作，获取操作前的数据
        if (sqlInfo.getOperationType() == OperationType.UPDATE ||
                sqlInfo.getOperationType() == OperationType.DELETE) {
            try {
                Map<String, Object> beforeData = transactionAwareEnhancedAuditService.getBeforeData(sqlInfo);
                sqlInfo.setOldData(beforeData);
            } catch (Exception e) {
                log.error("Get before data failed", e);
            }
        }

        // 执行原始操作
        Object result = point.proceed();

        // 如果是插入或更新操作，获取操作后的数据
        if (sqlInfo.getOperationType() == OperationType.INSERT ||
                sqlInfo.getOperationType() == OperationType.UPDATE) {
            try {
                Map<String, Object> afterData = transactionAwareEnhancedAuditService.getAfterData(sqlInfo);
                sqlInfo.setNewData(afterData);
            } catch (Exception e) {
                log.error("Get after data failed", e);
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
}
