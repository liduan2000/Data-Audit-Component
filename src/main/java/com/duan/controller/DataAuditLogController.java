package com.duan.controller;

import com.duan.entity.DataAuditLog;
import com.duan.repository.DataAuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/audit")
public class DataAuditLogController {

    private final DataAuditLogRepository dataAuditLogRepository;

    public DataAuditLogController(DataAuditLogRepository dataAuditLogRepository) {
        this.dataAuditLogRepository = dataAuditLogRepository;
    }

    /**
     * 查询指定表名和时间范围的审计日志
     *
     * @param tableName 表名
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @param page      当前页码（从0开始）
     * @param size      每页大小
     * @return 分页的审计日志
     */
    @GetMapping("/logs")
    public ResponseEntity<Page<DataAuditLog>> getAuditLogs(
            @RequestParam String tableName,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<DataAuditLog> auditLogs = dataAuditLogRepository.findByTableNameAndTimeRange(
                tableName, startTime, endTime, pageable);

        return ResponseEntity.ok(auditLogs);
    }

    /**
     * 获取所有已经审计的表名
     *
     * @return 表名列表
     */
    @GetMapping("/tables")
    public ResponseEntity<List<String>> getAllAuditedTables() {
        List<String> tables = dataAuditLogRepository.findAllAuditedTables();
        return ResponseEntity.ok(tables);
    }
}
