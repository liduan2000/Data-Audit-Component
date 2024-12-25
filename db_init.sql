DROP TABLE IF EXISTS sys_data_audit_log;
DROP DATABASE IF EXISTS sys_audit_db;

CREATE DATABASE IF NOT EXISTS sys_audit_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE sys_audit_db;

CREATE TABLE sys_data_audit_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    table_name VARCHAR(100) NOT NULL,
    operation_type VARCHAR(20) NOT NULL,
    primary_key_name VARCHAR(50),
    primary_key_value VARCHAR(100),
    old_value TEXT,
    new_value TEXT,
    operator VARCHAR(100),
    operate_time DATETIME NOT NULL,
    remark VARCHAR(500)
);

CREATE TABLE test_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    age INT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;