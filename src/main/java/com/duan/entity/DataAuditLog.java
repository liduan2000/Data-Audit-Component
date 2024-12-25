package com.duan.entity;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "sys_data_audit_log")
public class DataAuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String tableName;        // 表名
    private String operationType;    // 操作类型：INSERT/UPDATE/DELETE
    private String primaryKeyName;   // 主键名
    private String primaryKeyValue;  // 主键值
    private String oldValue;         // 修改前的值(JSON)
    private String newValue;         // 修改后的值(JSON)
    private String operator;         // 操作人
    private LocalDateTime operateTime; // 操作时间

    @Column(length = 500)
    private String remark;
}
