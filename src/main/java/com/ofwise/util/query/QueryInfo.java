package com.ofwise.util.query;

import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 查询信息类。QueryParser解析查询字符串，生成该对象。
 *
 * @author PONY
 */
public class QueryInfo {
    @Nullable
    private String orderBy;
    private String mainTable;
    private String tablePrefix;
    private boolean distinct = false;
    private List<JoinTable> joinTables = new ArrayList<>();
    private List<WhereCondition> whereConditions = new ArrayList<>();
    private Map<String, List<WhereCondition>> whereOrConditions = new HashMap<>();
    private Map<String, Map<String, List<WhereCondition>>> whereOrAndConditions = new HashMap<>();

    public QueryInfo(String mainTable, String tablePrefix) {
        this.mainTable = mainTable;
        this.tablePrefix = tablePrefix;
    }

    public String getTableName() {
        return this.tablePrefix + this.mainTable;
    }

    @Nullable
    public String getOrderBy() {
        return orderBy;
    }

    public void setOrderBy(@Nullable String orderBy) {
        QueryUtils.validateOrderBy(orderBy);
        this.orderBy = orderBy;
    }

    public String getMainTable() {
        return mainTable;
    }

    public void setMainTable(String mainTable) {
        this.mainTable = mainTable;
    }

    public String getTablePrefix() {
        return tablePrefix;
    }

    public void setTablePrefix(String tablePrefix) {
        this.tablePrefix = tablePrefix;
    }

    public boolean isDistinct() {
        return distinct;
    }

    public void setDistinct(boolean distinct) {
        this.distinct = distinct;
    }

    public List<JoinTable> getJoinTables() {
        return joinTables;
    }

    public void setJoinTables(List<JoinTable> joinTables) {
        this.joinTables = joinTables;
    }

    public List<WhereCondition> getWhereConditions() {
        return whereConditions;
    }

    public void setWhereConditions(List<WhereCondition> whereConditions) {
        this.whereConditions = whereConditions;
    }

    public Map<String, List<WhereCondition>> getWhereOrConditions() {
        return whereOrConditions;
    }

    public void setWhereOrConditions(Map<String, List<WhereCondition>> whereOrConditions) {
        this.whereOrConditions = whereOrConditions;
    }

    public Map<String, Map<String, List<WhereCondition>>> getWhereOrAndConditions() {
        return whereOrAndConditions;
    }

    public void setWhereOrAndConditions(Map<String, Map<String, List<WhereCondition>>> whereOrAndConditions) {
        this.whereOrAndConditions = whereOrAndConditions;
    }

    public static class WhereCondition {
        private String column;
        private String operator;
        @Nullable
        private Object value;

        public WhereCondition(String column, String operator, @Nullable Object value) {
            QueryUtils.validateField(column);
            this.column = column;
            this.operator = operator;
            this.value = value;
        }

        public String getColumn() {
            return column;
        }

        public void setColumn(String column) {
            this.column = column;
        }

        public String getOperator() {
            return operator;
        }

        public void setOperator(String operator) {
            this.operator = operator;
        }

        @Nullable
        public Object getValue() {
            return value;
        }

        public void setValue(@Nullable Object value) {
            this.value = value;
        }
    }

    public static class JoinTable {
        private String tableName;
        private String tableAlias;
        private String leftId;

        public JoinTable(String tableName, String tableAlias, String leftId, String rightId) {
            QueryUtils.validateTable(tableName);
            QueryUtils.validateTable(tableAlias);
            QueryUtils.validateField(leftId);
            QueryUtils.validateField(rightId);
            this.tableName = tableName;
            this.tableAlias = tableAlias;
            this.leftId = leftId;
            this.rightId = rightId;
        }

        private String rightId;

        public String getTableName() {
            return tableName;
        }

        public void setTableName(String tableName) {
            this.tableName = tableName;
        }

        public String getTableAlias() {
            return tableAlias;
        }

        public void setTableAlias(String tableAlias) {
            this.tableAlias = tableAlias;
        }

        public String getLeftId() {
            return leftId;
        }

        public void setLeftId(String leftId) {
            this.leftId = leftId;
        }

        public String getRightId() {
            return rightId;
        }

        public void setRightId(String rightId) {
            this.rightId = rightId;
        }

        @Override
        public String toString() {
            return "JoinTable{" +
                    "tableName='" + tableName + '\'' +
                    ", tableAlias='" + tableAlias + '\'' +
                    ", leftId='" + leftId + '\'' +
                    ", rightId='" + rightId + '\'' +
                    '}';
        }
    }

}
