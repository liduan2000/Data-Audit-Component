package com.duan.utils;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLExpr;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.expr.*;
import com.alibaba.druid.sql.ast.statement.SQLUpdateSetItem;
import com.alibaba.druid.sql.dialect.mysql.ast.statement.MySqlDeleteStatement;
import com.alibaba.druid.sql.dialect.mysql.ast.statement.MySqlInsertStatement;
import com.alibaba.druid.sql.dialect.mysql.ast.statement.MySqlUpdateStatement;
import com.duan.enums.OperationType;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class SQLParser {

    public SQLInfo parseSql(String sql) {
        try {
            SQLStatement statement = SQLUtils.parseSingleStatement(sql, DbType.mysql);
            SQLInfo sqlInfo = new SQLInfo();

            if (statement instanceof MySqlInsertStatement) {
                handleInsert((MySqlInsertStatement) statement, sqlInfo);
            } else if (statement instanceof MySqlUpdateStatement) {
                handleUpdate((MySqlUpdateStatement) statement, sqlInfo);
            } else if (statement instanceof MySqlDeleteStatement) {
                handleDelete((MySqlDeleteStatement) statement, sqlInfo);
            }

            return sqlInfo;
        } catch (Exception e) {
            log.error("Parse SQL failed: {}", sql, e);
            return null;
        }
    }

    private static void handleInsert(MySqlInsertStatement insert, SQLInfo sqlInfo) {
        sqlInfo.setTableName(insert.getTableName().getSimpleName());
        sqlInfo.setOperationType(OperationType.INSERT);

        List<SQLExpr> values = insert.getValues().getValues();
        List<SQLExpr> columns = insert.getColumns();

        Map<String, Object> newData = new HashMap<>();
        for (int i = 0; i < columns.size(); i++) {
            String column = ((SQLIdentifierExpr) columns.get(i)).getName();
            Object value = parseValue(values.get(i));
            newData.put(column, value);
        }

        sqlInfo.setNewData(newData);
    }

    private static void handleUpdate(MySqlUpdateStatement update, SQLInfo sqlInfo) {
        sqlInfo.setTableName(update.getTableSource().toString());
        sqlInfo.setOperationType(OperationType.UPDATE);
        sqlInfo.setWhereClause(update.getWhere().toString());

        Map<String, Object> newData = new HashMap<>();
        for (SQLUpdateSetItem item : update.getItems()) {
            String column = item.getColumn().toString();
            Object value = parseValue(item.getValue());
            newData.put(column, value);
        }

        sqlInfo.setNewData(newData);
    }

    private static void handleDelete(MySqlDeleteStatement delete, SQLInfo sqlInfo) {
        sqlInfo.setTableName(delete.getTableName().getSimpleName());
        sqlInfo.setOperationType(OperationType.DELETE);
        sqlInfo.setWhereClause(delete.getWhere().toString());
    }

    private static Object parseValue(SQLExpr expr) {
        if (expr instanceof SQLNumberExpr) {
            return ((SQLNumberExpr) expr).getNumber();
        } else if (expr instanceof SQLCharExpr) {
            return ((SQLCharExpr) expr).getText();
        } else if (expr instanceof SQLBooleanExpr) {
            return ((SQLBooleanExpr) expr).getValue();
        } else if (expr instanceof SQLNullExpr) {
            return null;
        }
        return expr.toString();
    }
}
