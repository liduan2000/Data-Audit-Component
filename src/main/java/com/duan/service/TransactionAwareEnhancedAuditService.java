package com.duan.service;

import com.duan.config.AuditConfig;
import com.duan.entity.DataAuditLog;
import com.duan.metadata.TableMetadataProvider;
import com.duan.repository.DataAuditLogRepository;
import com.duan.utils.SQLInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class TransactionAwareEnhancedAuditService extends EnhancedAuditService {
    private final ApplicationEventPublisher eventPublisher;
    private final ThreadLocal<Map<String, List<DataAuditLog>>> transactionAuditLogs =
            ThreadLocal.withInitial(ConcurrentHashMap::new);

    public TransactionAwareEnhancedAuditService(AuditConfig auditConfig,
                                                DataAuditLogRepository dataAuditLogRepository,
                                                JdbcTemplate jdbcTemplate,
                                                TableMetadataProvider metadataProvider,
                                                ApplicationEventPublisher eventPublisher) {
        super(auditConfig, dataAuditLogRepository, jdbcTemplate, metadataProvider);
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional(propagation = Propagation.SUPPORTS)
    public void saveAuditLog(SQLInfo sqlInfo) {
        if (!needAudit(sqlInfo)) {
            return;
        }

        try {
            DataAuditLog log = createAuditLog(sqlInfo);

            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                // 在事务中，将日志添加到当前事务的集合中
                String txId = TransactionSynchronizationManager.getCurrentTransactionName();
                addLogToCurrentTransaction(txId, log);
                registerSynchronizationIfNeeded(txId);
            } else {
                // 不在事务中，直接保存
                directSave(log);
            }
        } catch (Exception e) {
            log.error("Failed to handle audit log", e);
        }
    }

    private void addLogToCurrentTransaction(String txId, DataAuditLog log) {
        Map<String, List<DataAuditLog>> currentTransactionLogs = transactionAuditLogs.get();
        currentTransactionLogs.computeIfAbsent(txId, k -> new ArrayList<>()).add(log);
    }

    private void registerSynchronizationIfNeeded(String txId) {
        if (!isSynchronizationRegistered()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            // 只在事务提交时保存审计日志
                            Map<String, List<DataAuditLog>> allLogs = transactionAuditLogs.get();
                            List<DataAuditLog> logs = allLogs.get(txId);
                            if (logs != null && !logs.isEmpty()) {
                                saveAll(logs);
                            }
                        }

                        @Override
                        public void afterCompletion(int status) {
                            // 清理资源
                            cleanupThreadLocal(txId);
                        }
                    }
            );
        }
    }

    private boolean isSynchronizationRegistered() {
        return TransactionSynchronizationManager.getSynchronizations().stream()
                .anyMatch(sync -> sync.getClass().getName().contains(
                        TransactionAwareEnhancedAuditService.class.getName()));
    }

    private void cleanupThreadLocal(String txId) {
        try {
            Map<String, List<DataAuditLog>> allLogs = transactionAuditLogs.get();
            allLogs.remove(txId);
            if (allLogs.isEmpty()) {
                transactionAuditLogs.remove();
            }
        } catch (Exception e) {
            log.error("Error cleaning up ThreadLocal resources", e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void directSave(DataAuditLog dataAuditLog) {
        try {
            dataAuditLogRepository.save(dataAuditLog);
            eventPublisher.publishEvent(new AuditLogCommittedEvent(this, dataAuditLog));
        } catch (Exception e) {
            log.error("Failed to save audit log directly", e);
            retryDirectSave(dataAuditLog);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveAll(List<DataAuditLog> logs) {
        try {
            dataAuditLogRepository.saveAll(logs);
            eventPublisher.publishEvent(new AuditLogsCommittedEvent(this, logs));
        } catch (Exception e) {
            log.error("Failed to save audit logs in batch", e);
            logs.forEach(this::retryDirectSave);
        }
    }

    private void retryDirectSave(DataAuditLog dataAuditLog) {
        int retryCount = 0;
        while (retryCount < auditConfig.getMaxRetries()) {
            try {
                Thread.sleep(1000 * (retryCount + 1));
                dataAuditLogRepository.save(dataAuditLog);
                eventPublisher.publishEvent(new AuditLogCommittedEvent(this, dataAuditLog));
                return;
            } catch (Exception e) {
                log.error("Retry save audit log failed, attempt: {}", retryCount + 1, e);
                retryCount++;
            }
        }
    }

    @PreDestroy
    public void cleanup() {
        transactionAuditLogs.remove();
    }

    // 审计事件类
    public static class AuditLogCommittedEvent {
        private final Object source;
        private final DataAuditLog auditLog;

        public AuditLogCommittedEvent(Object source, DataAuditLog auditLog) {
            this.source = source;
            this.auditLog = auditLog;
        }

        public DataAuditLog getAuditLog() {
            return auditLog;
        }
    }

    public static class AuditLogsCommittedEvent {
        private final Object source;
        private final List<DataAuditLog> auditLogs;

        public AuditLogsCommittedEvent(Object source, List<DataAuditLog> auditLogs) {
            this.source = source;
            this.auditLogs = auditLogs;
        }

        public List<DataAuditLog> getAuditLogs() {
            return auditLogs;
        }
    }
}
