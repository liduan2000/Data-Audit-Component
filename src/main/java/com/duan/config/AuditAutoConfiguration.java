package com.duan.config;

import com.duan.aspect.JdbcTemplateAuditAspect;
import com.duan.aspect.JpaAuditAspect;
import com.duan.repository.DataAuditLogRepository;
import com.duan.service.AuditService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
@EnableConfigurationProperties(AuditConfig.class)
@ConditionalOnProperty(prefix = "audit", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AuditAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AuditService auditService(AuditConfig auditConfig,
                                     DataAuditLogRepository auditLogRepository,
                                     JdbcTemplate jdbcTemplate) {
        return new AuditService(auditConfig, auditLogRepository, jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public JdbcTemplateAuditAspect jdbcTemplateAuditAspect(AuditService auditService,
                                                           AuditConfig auditConfig) {
        return new JdbcTemplateAuditAspect(auditService, auditConfig);
    }

    @Bean
    @ConditionalOnMissingBean
    public JpaAuditAspect jpaAuditAspect(AuditService auditService,
                                         AuditConfig auditConfig) {
        return new JpaAuditAspect(auditService, auditConfig);
    }

    @Bean
    public Executor auditExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("audit-async-");
        executor.initialize();
        return executor;
    }
}
