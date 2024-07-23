package com.ujcms.commons.query;

import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.ujcms.commons.query.QueryUtils.*;

/**
 * Like_name
 * Like_name
 * Like_user-username
 * Like_user-userExt-username
 * Like_user-user@UserRole-realName
 * Like_questionExt-title
 * EQ_price_Int
 * Like_editUser@user-ext-username
 * Like_1_questionExt-title
 * Like_1_questionExt-markdown
 * In_1_status
 * Like_1-1_username
 * <p>
 * Distinct=true
 * OrderBy=publicDate_desc => order by t_.publicDate desc
 * username => t_.username_
 * user-username => user_.username_
 * editUser@user-username => editUser_.username_
 *
 * <ul>
 * <li>Distinct
 * <li>TableJoin
 * <li>WhereCondition
 * <li>OrderBy
 * </ul>
 * <p>
 * HTTP Parameter Name 支持的特殊字符 _ - @ $ * ! ~ ( ) :
 * <p>
 * Freemarker 标签参数支持 大写字母、小写字母、数字、下划线和$，不支持@和中划线
 *
 * @author PONY
 */
public class QueryParser {
    @Nullable
    public static QueryInfo parse(@Nullable Map<String, Object> params, String table) {
        return parse(params, table, null);
    }

    @Nullable
    public static QueryInfo parse(@Nullable Map<String, Object> params, String table,
                                  @Nullable String defaultOrderBy) {
        return parse(params, table, defaultOrderBy, TABLE_PREFIX);
    }

    private static boolean isContinueKey(QueryInfo info, String key, @Nullable Object value) {
        if (key.equalsIgnoreCase(DISTINCT)) {
            info.setDistinct(true);
            return true;
        }
        // 除了 IsNull 和 IsNotNull，其它的value都不能为null或空串。
        boolean nullOrBlank = value == null || (value instanceof String && StringUtils.isBlank((String) value));
        if (!StringUtils.startsWithAny(key, ORDER_BY, QueryUtils.OPERATOR_IS_NULL, QueryUtils.OPERATOR_IS_NOT_NULL)
                && nullOrBlank) {
            return true;
        }
        if (key.equals(ORDER_BY)) {
            if (value instanceof String) {
                parseOrderBy(info, (String) value);
            }
            return true;
        }
        return false;
    }

    @Nullable
    public static QueryInfo parse(@Nullable Map<String, Object> params, String table,
                                  @Nullable String defaultOrderBy, String tablePrefix) {
        if (params == null) {
            return null;
        }
        QueryInfo info = new QueryInfo(table, tablePrefix);
        parseOrderBy(info, defaultOrderBy);
        // 用于默认组
        int nextSubGroup = 1000;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            // EQ_price_Int
            // Like_username
            // Like_user-UserExt-realName
            // Like_editUser@user-ext-username
            // Like_1_questionExt-title
            // Like_1-1_questionExt-markdown
            // In_1_status
            String key = entry.getKey();
            QueryUtils.validateQuery(key);
            Object value = entry.getValue();
            if (isContinueKey(info, key, value)) {
                continue;
            }
            int index = key.indexOf(UNDERLINE);
            if (index == -1) {
                throw new IllegalArgumentException("Illegal query key format: " + key);
            }
            // 获取操作符
            // EQ
            // Like
            // In
            String operator = key.substring(0, index);
            // price_Int
            // username
            // user-UserExt-realName
            // editUser@user-ext-username
            // 1_questionExt-title
            // 1-1_questionExt-markdown
            // 1_status
            key = key.substring(index + 1);
            if (key.length() < 1) {
                throw new IllegalArgumentException("Illegal query key format: " + key);
            }
            index = key.indexOf(UNDERLINE, Character.isDigit(key.charAt(0)) ? key.indexOf(UNDERLINE) + 1 : 0);
            // 获取表及字段
            // price
            // username
            // user-UserExt-realName
            // editUser@user-ext-username
            // 1_questionExt-title
            // 1-1_questionExt-markdown
            // 1_status
            String columnPart;
            String type = QueryUtils.TYPE_STRING;
            // 后面没有类型
            if (index == -1) {
                columnPart = key;
            } else {
                columnPart = key.substring(0, index);
                // Int
                type = key.substring(index + 1);
            }
            parseWhereCondition(info, columnPart, operator, value, type, nextSubGroup);
            nextSubGroup += 1;
        }
        return info;
    }

    private static void parseOrderBy(QueryInfo info, @Nullable String rawOrderBy) {
        // username, created_desc
        // username_desc
        // user-UserExt-realName_desc
        if (rawOrderBy == null) {
            return;
        }
        StringBuilder selectBuff = new StringBuilder();
        List<QueryInfo.OrderByCondition> orderByConditions = new ArrayList<>();
        for (String orderBy : StringUtils.split(rawOrderBy, COMMA)) {
            orderBy = orderBy.trim();
            QueryUtils.validateQuery(orderBy);
            String direction = null;
            String type = null;
            int index = orderBy.lastIndexOf("_");
            if (index != -1) {
                direction = orderBy.substring(index + 1);
                orderBy = orderBy.substring(0, index);
                if (DIRECTION_ASC.equals(direction) || DIRECTION_DESC.equals(direction)) {
                    index = orderBy.indexOf(UNDERLINE);
                    if (index != -1) {
                        type = orderBy.substring(index + 1);
                        orderBy = orderBy.substring(0, index);
                    }
                } else {
                    type = direction;
                    direction = null;
                }
            }
            // order by 去掉 desc，用于生成 table join。
            String column = parseJoinTable(info, orderBy);
            String jsonColumn = null;
            // 是否json查询
            int jsonIndex = column.indexOf(DOLLAR);
            if (jsonIndex >= 0) {
                jsonColumn = underscoreToCamel(column.substring(jsonIndex + 1));
                column = column.substring(0, jsonIndex);
            }
            orderByConditions.add(new QueryInfo.OrderByCondition(column, direction, jsonColumn, type));
            if (!column.startsWith(MAIN_TABLE_ALIAS + POINT)) {
                selectBuff.append(COMMA).append(column);
            }
        }
        info.setOrderByConditions(orderByConditions);
        info.setSelectOrderBy(selectBuff.length() > 0 ? selectBuff.toString() : null);
    }

    private static void parseWhereCondition(QueryInfo info, String columnPart, String operator,
                                            @Nullable Object obj, String type, int nextSubGroup) {
        // price
        // username
        // user-UserExt-realName
        // editUser@user-ext-username
        // 1_questionExt-title
        // 1-1_questionExt-markdown
        // 1_status

        // 是否分组
        int groupIndex = columnPart.indexOf("_");
        String group = null;
        if (groupIndex >= 0) {
            group = columnPart.substring(0, groupIndex);
            columnPart = columnPart.substring(groupIndex + 1);
        }
        String column = parseJoinTable(info, columnPart);
        String jsonColumn = null;
        // 是否json查询
        int jsonIndex = column.indexOf(DOLLAR);
        if (jsonIndex >= 0) {
            jsonColumn = underscoreToCamel(column.substring(jsonIndex + 1));
            column = column.substring(0, jsonIndex);
        }
        Object value = QueryUtils.getValue(type, obj, operator);
        if (group != null) {
            groupIndex = group.indexOf("-");
            // 默认组
            String subGroup = String.valueOf(nextSubGroup);
            if (groupIndex != -1) {
                subGroup = group.substring(groupIndex + 1);
                group = group.substring(0, groupIndex);
            }
            Map<String, List<QueryInfo.WhereCondition>> orConditions =
                    info.getWhereOrAndConditions().computeIfAbsent(group, k -> new HashMap<>(16));
            List<QueryInfo.WhereCondition> orAndConditions =
                    orConditions.computeIfAbsent(subGroup, k -> new ArrayList<>());
            orAndConditions.add(new QueryInfo.WhereCondition(column, jsonColumn,
                    QueryUtils.getOperator(operator), QueryUtils.isArrayQuery(operator), value, type));
        } else {
            info.getWhereConditions().add(new QueryInfo.WhereCondition(column, jsonColumn,
                    QueryUtils.getOperator(operator), QueryUtils.isArrayQuery(operator), value, type));
        }
    }

    /**
     * JOIN rightTable rightAlias on leftAlias.leftId = rightAlias.rightId
     */
    private static String parseJoinTable(QueryInfo info, String columnPart) {
        // editUser@user-@userExt-username

        // 分组标识无需处理，需要去除标识。
        // 1级分组：1_questionExt-title 1_questionExt-markdown
        // 2级分组：1-1_status 1-1_username
        int underIndex = columnPart.indexOf(UNDERLINE);
        if (underIndex >= 0) {
            columnPart = columnPart.substring(underIndex + 1);
        }
        // tables = [editUser@user | userExt | username]
        List<String> tables = Stream.of(columnPart.split(DASH)).collect(Collectors.toList());
        // 最后一个是字段名，不是表名，去除
        // tables = [editUser@user | userExt]
        String column = tables.remove(tables.size() - 1);

        String leftAlias = MAIN_TABLE_ALIAS;
        String leftTable = info.getMainTable();

        // [editUser@user, @userExt]
        // [user, @userExt]
        // [@questionExt]
        // [user, UserExt]
        // [user, UserRole]
        // [user, UserRole, role]
        // [user, custom1@UserCustom]
        for (String tablePart : tables) {
            ParsedTable parsedTable = new ParsedTable(tablePart);
            parsedTable.firstAt();
            parsedTable.upperCaseTable();
            parsedTable.leftIdAndRightId(leftTable);

            String leftAliasId = leftAlias + POINT + parsedTable.getLeftId();
            String rightAliasId = parsedTable.getRightAlias() + POINT + parsedTable.getRightId();
            String finalRightAlias = parsedTable.getRightAlias();
            if (info.getJoinTables().stream().noneMatch(it -> it.getTableAlias().equalsIgnoreCase(finalRightAlias))) {
                String tableName = info.getTablePrefix() + camelToUnderscore(parsedTable.getRightTable());
                info.getJoinTables().add(new QueryInfo.JoinTable(tableName, parsedTable.getRightAlias(),
                        leftAliasId, rightAliasId));
            }
            leftAlias = parsedTable.getRightAlias();
            leftTable = parsedTable.getRightTable();
        }
        return leftAlias + POINT + camelToUnderscore(column) + UNDERLINE;
    }

    private static final String MAIN_TABLE_ALIAS = "t";

    public static final String TABLE_PREFIX = "ujcms_";
    public static final String DISTINCT = "Distinct";
    public static final String ORDER_BY = "OrderBy";

    /**
     * 五种情况
     * <li> many-to-one: user                   -> user, user_, t.user_id_, user.id_
     * <li> many-to-one: editUser@user          -> user, editUser_, t.edit_user_id_, editUser_.id_
     * <li> one-to-one : @userExt               -> user_ext, userExt_, t.id_, userExt_.id_
     * <li> one-to-many: UserRole               -> user_role, userRole_, t.id_, userRole_.user_id_ (user_id_ 通过leftTable推断)
     * <li> one-to-many: descendant@OrgTree     -> org_tree, orgTree_, t.id_, orgTree_.descendant_id_
     * <li> one-to-many: org@OrgTree@descendant -> org_tree, orgTree_, t.org_id_, orgTree_.descendant_id_
     */
    private static final class ParsedTable {
        private String tablePart;
        private String leftColumn = "";
        private String rightColumn = "";
        private String rightAlias;
        private String rightTable;
        private String leftId = "";
        private String rightId = "";
        private boolean one2one = false;
        private boolean one2many = false;

        public ParsedTable(String tablePart) {
            this.tablePart = tablePart;
            this.rightAlias = tablePart;
            this.rightTable = tablePart;
        }

        /**
         * 处理第一个 @
         */
        public void firstAt() {
            int atIndex = tablePart.indexOf("@");
            if (atIndex >= 0) {
                // editUser
                leftColumn = tablePart.substring(0, atIndex);
                rightAlias = leftColumn;
                // user
                // questionExt
                // UserExt
                rightTable = tablePart.substring(atIndex + 1);
                // 处理 one2one 情形 @userExt @questionExt。此时rightAlias为空串
                if (leftColumn.length() == 0) {
                    one2one = true;
                    rightAlias = rightTable;
                }
            }
        }

        /**
         * 首字母大写，为one2many
         */
        public void upperCaseTable() {
            if (Character.isUpperCase(rightTable.charAt(0))) {
                one2many = true;
                // 首字母换回小写
                rightTable = rightTable.substring(0, 1).toLowerCase() + rightTable.substring(1);
                // one2many 双 @ 情景：org@OrgTree@descendant
                int atIndex = rightTable.indexOf("@");
                if (atIndex >= 0) {
                    rightColumn = rightTable.substring(atIndex + 1);
                    rightTable = rightTable.substring(0, atIndex);
                }
                rightAlias = rightTable;
            }
        }

        public void leftIdAndRightId(String leftTable) {
            // 表别名下划线结尾，以免与数据库关键字冲突
            // user_
            // editUser_
            // questionExt_
            // userExt_
            rightAlias = rightAlias + "_";
            // t_.id_
            // t_.user_id_
            // t_.edit_user_id_
            leftId = camelToUnderscore(rightAlias) + "id_";
            if (one2many || one2one) {
                leftId = "id_";
                if (!rightColumn.isEmpty()) {
                    leftId = camelToUnderscore(leftColumn) + "_id_";
                }
            }
            // user_.id_
            // userExt_.user_id_
            rightId = "id_";
            if (one2many) {
                if (leftColumn.isEmpty()) {
                    // origAlias为空，使用 leftTable 推断
                    rightId = camelToUnderscore(leftTable) + "_id_";
                } else {
                    rightId = camelToUnderscore(rightColumn.isEmpty() ? leftColumn : rightColumn) + "_id_";
                }
            }
        }

        public String getTablePart() {
            return tablePart;
        }

        public void setTablePart(String tablePart) {
            this.tablePart = tablePart;
        }

        public String getLeftColumn() {
            return leftColumn;
        }

        public void setLeftColumn(String leftColumn) {
            this.leftColumn = leftColumn;
        }

        public String getRightColumn() {
            return rightColumn;
        }

        public void setRightColumn(String rightColumn) {
            this.rightColumn = rightColumn;
        }

        public String getRightAlias() {
            return rightAlias;
        }

        public void setRightAlias(String rightAlias) {
            this.rightAlias = rightAlias;
        }

        public String getRightTable() {
            return rightTable;
        }

        public void setRightTable(String rightTable) {
            this.rightTable = rightTable;
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
    }

    /**
     * 工具类不需要实例化
     */
    private QueryParser() {
        throw new IllegalStateException("Utility class");
    }
}
