package com.ujcms.commons.query;

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
    private String selectOrderBy;
    private String mainTable;
    private String tablePrefix;
    private boolean distinct = false;
    private List<JoinTable> joinTables = new ArrayList<>();
    private List<WhereCondition> whereConditions = new ArrayList<>();
    private Map<String, Map<String, List<WhereCondition>>> whereOrAndConditions = new HashMap<>();
    private List<OrderByCondition> orderByConditions = new ArrayList<>();

    public QueryInfo(String mainTable, String tablePrefix) {
        this.mainTable = mainTable;
        this.tablePrefix = tablePrefix;
    }

    public String getTableName() {
        return this.tablePrefix + this.mainTable;
    }

    @Nullable
    public String getSelectOrderBy() {
        return selectOrderBy;
    }

    public void setSelectOrderBy(@Nullable String selectOrderBy) {
        QueryUtils.validateOrderBy(selectOrderBy);
        this.selectOrderBy = selectOrderBy;
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

    public Map<String, Map<String, List<WhereCondition>>> getWhereOrAndConditions() {
        return whereOrAndConditions;
    }

    public void setWhereOrAndConditions(Map<String, Map<String, List<WhereCondition>>> whereOrAndConditions) {
        this.whereOrAndConditions = whereOrAndConditions;
    }

    public List<OrderByCondition> getOrderByConditions() {
        return orderByConditions;
    }

    public void setOrderByConditions(List<OrderByCondition> orderByConditions) {
        this.orderByConditions = orderByConditions;
    }

    public static class WhereCondition {
        private String column;
        @Nullable
        private String jsonColumn;
        private String operator;
        private boolean array = false;
        @Nullable
        private Object value;
        @Nullable
        private String type;

        public WhereCondition(String column, String operator, @Nullable Object value) {
            QueryUtils.validateField(column);
            this.column = column;
            this.operator = operator;
            this.value = value;
        }

        public WhereCondition(String column, @Nullable String jsonColumn, String operator, boolean array,
                              @Nullable Object value, @Nullable String type) {
            this(column, operator, value);
            if (jsonColumn != null) {
                QueryUtils.validateJsonField(jsonColumn);
                this.jsonColumn = jsonColumn;
            }
            this.array = array;
            this.type = type;
        }

        public String getColumn() {
            return column;
        }

        public void setColumn(String column) {
            this.column = column;
        }

        @Nullable
        public String getJsonColumn() {
            return jsonColumn;
        }

        public void setJsonColumn(@Nullable String jsonColumn) {
            this.jsonColumn = jsonColumn;
        }

        public String getOperator() {
            return operator;
        }

        public void setOperator(String operator) {
            this.operator = operator;
        }

        public boolean isArray() {
            return array;
        }

        public void setArray(boolean array) {
            this.array = array;
        }

        @Nullable
        public Object getValue() {
            return value;
        }

        public void setValue(@Nullable Object value) {
            this.value = value;
        }

        @Nullable
        public String getType() {
            return type;
        }

        public void setType(@Nullable String type) {
            this.type = type;
        }

        @Override
        public String toString() {
            return "WhereCondition{" +
                    "column='" + column + '\'' +
                    ", jsonColumn='" + jsonColumn + '\'' +
                    ", operator='" + operator + '\'' +
                    ", array=" + array +
                    ", value=" + value +
                    ", type='" + type + '\'' +
                    '}';
        }
    }

    public static class OrderByCondition {
        private String column;
        @Nullable
        private String jsonColumn;
        @Nullable
        private String type;
        @Nullable
        private String direction;

        public OrderByCondition(String column, @Nullable String direction) {
            QueryUtils.validateField(column);
            this.column = column;
            this.direction = direction;
        }

        public OrderByCondition(String column, @Nullable String direction,
                                @Nullable String jsonColumn, @Nullable String type) {
            this(column, direction);
            if (jsonColumn != null) {
                QueryUtils.validateJsonField(jsonColumn);
                this.jsonColumn = jsonColumn;
            }
            this.type = type;
        }

        public String getColumn() {
            return column;
        }

        public void setColumn(String column) {
            this.column = column;
        }

        @Nullable
        public String getJsonColumn() {
            return jsonColumn;
        }

        public void setJsonColumn(@Nullable String jsonColumn) {
            this.jsonColumn = jsonColumn;
        }

        @Nullable
        public String getType() {
            return type;
        }

        public void setType(@Nullable String type) {
            this.type = type;
        }

        @Nullable
        public String getDirection() {
            return direction;
        }

        public void setDirection(@Nullable String direction) {
            this.direction = direction;
        }

        @Override
        public String toString() {
            return "OrderByCondition{" +
                    "column='" + column + '\'' +
                    ", jsonColumn='" + jsonColumn + '\'' +
                    ", type='" + type + '\'' +
                    ", direction='" + direction + '\'' +
                    '}';
        }
    }

    public static class JoinTable {
        private String tableName;
        private String tableAlias;
        private String leftId;
        private String rightId;

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
