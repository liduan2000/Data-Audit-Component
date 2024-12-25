# 数据审计组件

## 使用方式

1. 引入依赖

```xml

<dependency>
    <groupId>com.duan</groupId>
    <artifactId>Data-Audit-Component</artifactId>
    <version>1.0.0</version>
</dependency>
```

2. 在启动类上添加注解

```java

@EnableDataAudit
@SpringBootApplication
public class YourApplication {
    public static void main(String[] args) {
        SpringApplication.run(YourApplication.class, args);
    }
}
```

3. 在配置文件中配置审计规则（可选）

```yaml
audit:
  enabled: true
  includeTables:
    - user
    - order
  excludeTables:
    - temp_table
  includeColumns:
    user:
      - name
      - email
  async: true
  maxRetries: 3
```

4. 创建审计日志表

```shell
source db_init.sql
```

## 主要特性

自动审计：无需修改业务代码，自动捕获所有通过 JdbcTemplate 执行的 DML 操作
配置灵活：支持启用/禁用，包含/排除表，指定字段等
异步处理：默认异步记录审计日志，不影响主流程性能
容错处理：审计失败有重试机制，且不影响主流程
轻量级：不依赖特定数据库，只需要简单配置即可使用

## 扩展建议

如果需要支持 MyBatis，可以添加对应的拦截器
可以添加 API 接口查询审计日志
可以实现 Web 界面展示审计数据
可以添加数据压缩和清理功能