package com.duan.repository;

import com.duan.entity.DataAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DataAuditLogRepository extends JpaRepository<DataAuditLog, Long> {
    @Query("SELECT d FROM DataAuditLog d WHERE d.tableName = :tableName " +
            "AND d.operateTime BETWEEN :startTime AND :endTime")
    Page<DataAuditLog> findByTableNameAndTimeRange(
            @Param("tableName") String tableName,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            Pageable pageable
    );

    @Query("SELECT DISTINCT d.tableName FROM DataAuditLog d")
    List<String> findAllAuditedTables();
}
