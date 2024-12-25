package com.duan.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

@Data
@Configuration
@ConfigurationProperties(prefix = "audit")
public class AuditConfig {
    private boolean enabled = true;
    private List<String> includeTables;
    private List<String> excludeTables;
    private Map<String, List<String>> includeColumns;
    private int maxRetries = 3;
    private boolean async = true;
}
