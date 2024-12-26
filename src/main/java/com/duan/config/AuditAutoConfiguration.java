package com.duan.config;

import com.duan.aspect.JdbcTemplateAuditAspect;
import com.duan.aspect.JpaAuditAspect;
import com.duan.metadata.MySqlTableMetadataProvider;
import com.duan.metadata.TableMetadataProvider;
import com.duan.repository.DataAuditLogRepository;
import com.duan.service.TransactionAwareEnhancedAuditService;
import com.duan.utils.EnhancedSQLParser;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.util.Arrays;

@Configuration
//@EnableAsync
@EnableCaching
@EnableTransactionManagement
@EnableConfigurationProperties(AuditConfig.class)
@ConditionalOnProperty(prefix = "audit", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AuditAutoConfiguration {
    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(Arrays.asList(
                new ConcurrentMapCache("tableMetadata")
        ));
        return cacheManager;
    }

    @Bean
    @ConditionalOnMissingBean
    public TableMetadataProvider tableMetadataProvider(JdbcTemplate jdbcTemplate,
                                                       CacheManager cacheManager) {
        return new MySqlTableMetadataProvider(jdbcTemplate, cacheManager);
    }

    @Bean
    @ConditionalOnMissingBean
    public EnhancedSQLParser enhancedSQLParser(TableMetadataProvider metadataProvider) {
        return new EnhancedSQLParser(metadataProvider);
    }

//    @Bean
//    @ConditionalOnMissingBean
//    public EnhancedAuditService auditService(AuditConfig auditConfig,
//                                             DataAuditLogRepository auditLogRepository,
//                                             JdbcTemplate jdbcTemplate,
//                                             TableMetadataProvider tableMetadataProvider) {
//        return new EnhancedAuditService(auditConfig, auditLogRepository, jdbcTemplate, tableMetadataProvider);
//    }

    @Bean
    @ConditionalOnMissingBean
    public TransactionAwareEnhancedAuditService transactionAwareEnhancedAuditService(
            AuditConfig auditConfig,
            DataAuditLogRepository dataAuditLogRepository,
            JdbcTemplate jdbcTemplate,
            TableMetadataProvider metadataProvider,
            ApplicationEventPublisher eventPublisher) {
        return new TransactionAwareEnhancedAuditService(
                auditConfig,
                dataAuditLogRepository,
                jdbcTemplate,
                metadataProvider,
                eventPublisher);
    }

    @Bean
    @ConditionalOnMissingBean
    public JdbcTemplateAuditAspect jdbcTemplateAuditAspect(TransactionAwareEnhancedAuditService transactionAwareEnhancedAuditService,
                                                           AuditConfig auditConfig,
                                                           EnhancedSQLParser sqlParser) {
        return new JdbcTemplateAuditAspect(transactionAwareEnhancedAuditService, auditConfig, sqlParser);
    }

    @Bean
    @ConditionalOnMissingBean
    public JpaAuditAspect jpaAuditAspect(TransactionAwareEnhancedAuditService transactionAwareEnhancedAuditService,
                                         AuditConfig auditConfig,
                                         TableMetadataProvider tableMetadataProvider) {
        return new JpaAuditAspect(transactionAwareEnhancedAuditService, auditConfig, tableMetadataProvider);
    }

//    @Bean
//    public Executor auditExecutor() {
//        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
//        executor.setCorePoolSize(2);
//        executor.setMaxPoolSize(5);
//        executor.setQueueCapacity(100);
//        executor.setThreadNamePrefix("audit-async-");
//        executor.initialize();
//        return executor;
//    }
}
