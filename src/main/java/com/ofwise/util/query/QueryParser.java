package com.ofwise.util.query;

import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
 * OrderBy=publicDate desc => order by t_.publicDate desc
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

    @Nullable
    public static QueryInfo parse(@Nullable Map<String, Object> params, String table,
                                  @Nullable String defaultOrderBy, String tablePrefix) {
        if (params == null) {
            return null;
        }
        QueryInfo info = new QueryInfo(table, tablePrefix);
        String rawOrderBy = defaultOrderBy;
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
            if (key.equalsIgnoreCase(DISTINCT)) {
                info.setDistinct(true);
                continue;
            }
            // 除了 IsNull 和 IsNotNull，其它的value都不能为null或空串。
            boolean nullOrBlank = value == null || (value instanceof String && StringUtils.isBlank((String) value));
            if (!StringUtils.startsWithAny(key, ORDER_BY, QueryUtils.OPERATOR_IS_NULL, QueryUtils.OPERATOR_IS_NOT_NULL) && nullOrBlank) {
                continue;
            }
            if (key.equals(ORDER_BY)) {
                if (value instanceof String) {
                    rawOrderBy = (String) value;
                }
                continue;
            }
            int index = key.indexOf("_");
            if (index == -1) {
                throw new RuntimeException("Illegal query key format: " + key);
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
                throw new RuntimeException("Illegal query key format: " + key);
            }
            index = key.indexOf("_", Character.isDigit(key.charAt(0)) ? key.indexOf("_") + 1 : 0);
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
            parseWhereCondition(info, columnPart, operator, value, type);
        }
        parseOrderBy(info, rawOrderBy);
        return info;
    }

    private static void parseOrderBy(QueryInfo info, @Nullable String rawOrderBy) {
        // username, created_desc
        // username_desc
        // user-UserExt-realName_desc
        if (rawOrderBy == null) {
            return;
        }
        StringBuilder buff = new StringBuilder();
        String comma = ",";
        for (String orderBy : StringUtils.split(rawOrderBy, comma)) {
            orderBy = orderBy.trim();
            QueryUtils.validateQuery(orderBy);
            int index = orderBy.indexOf("_");
            String direction = "";
            if (index != -1) {
                direction = orderBy.substring(index + 1);
                orderBy = orderBy.substring(0, index);
            }
            // order by 去掉 desc，用于生成 table join。
            buff.append(parseJoinTable(info, orderBy)).append(" ").append(direction).append(comma);
        }
        info.setOrderBy(buff.length() > 0 ? buff.substring(0, buff.length() - 1) : null);
    }

    private static void parseWhereCondition(QueryInfo info, String columnPart, String operator,
                                            @Nullable Object obj, String type) {
        // price
        // username
        // user-UserExt-realName
        // editUser@user-ext-username
        // 1_questionExt-title
        // 1-1_questionExt-markdown
        // 1_status

        // 是否分组
        int index = columnPart.indexOf("_");
        String group = null;
        if (index >= 0) {
            group = columnPart.substring(0, index);
            columnPart = columnPart.substring(index + 1);
        }
        String column = parseJoinTable(info, columnPart);
        Object value = QueryUtils.getValue(type, obj, operator);
        if (group != null) {
            index = group.indexOf("-");
            String subGroup = null;
            if (index != -1) {
                subGroup = group.substring(index + 1);
                group = group.substring(0, index);
            }
            if (subGroup != null) {
                Map<String, List<QueryInfo.WhereCondition>> subWhereOrConditions =
                        info.getWhereOrAndConditions().computeIfAbsent(group, k -> new HashMap<>(16));
                List<QueryInfo.WhereCondition> conditions =
                        subWhereOrConditions.computeIfAbsent(subGroup, k -> new ArrayList<>());
                conditions.add(new QueryInfo.WhereCondition(column, QueryUtils.getOperator(operator), value));
            } else {
                List<QueryInfo.WhereCondition> conditions = info.getWhereOrConditions().computeIfAbsent(group, k -> new ArrayList<>());
                conditions.add(new QueryInfo.WhereCondition(column, QueryUtils.getOperator(operator), value));
            }
        } else {
            info.getWhereConditions().add(new QueryInfo.WhereCondition(column, QueryUtils.getOperator(operator), value));
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
        int underIndex = columnPart.indexOf("_");
        if (underIndex >= 0) {
            columnPart = columnPart.substring(underIndex + 1);
        }
        // tables = [editUser@user | userExt | username]
        List<String> tables = Stream.of(columnPart.split("-")).collect(Collectors.toList());
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
            // 五种情况
            // many-to-one: user                   -> user, user_, t.user_id_, user.id_
            // many-to-one: editUser@user          -> user, editUser_, t.edit_user_id_, editUser_.id_
            // one-to-one : @userExt               -> user_ext, userExt_, t.id_, userExt_.id_
            // one-to-many: UserRole               -> user_role, userRole_, t.id_, userRole_.user_id_ (user_id_ 通过leftTable推断)
            // one-to-many: descendant@OrgTree     -> org_tree, orgTree_, t.id_, orgTree_.descendant_id_
            // one-to-many: org@OrgTree@descendant -> org_tree, orgTree_, t.org_id_, orgTree_.descendant_id_
            String leftColumn = "";
            String rightColumn = "";
            String rightAlias = tablePart;
            String rightTable = tablePart;
            int atIndex = tablePart.indexOf("@");
            boolean one2one = false;
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
            boolean one2many = false;
            // 首字母大写，为one2many
            if (Character.isUpperCase(rightTable.charAt(0))) {
                one2many = true;
                // 首字母换回小写
                rightTable = rightTable.substring(0, 1).toLowerCase() + rightTable.substring(1);
                // one2many 双 @ 情景：org@OrgTree@descendant
                atIndex = rightTable.indexOf("@");
                if (atIndex >= 0) {
                    rightColumn = rightTable.substring(atIndex + 1);
                    rightTable = rightTable.substring(0, atIndex);
                }
                rightAlias = rightTable;
            }
            // 表别名下划线结尾，以免与数据库关键字冲突
            // user_
            // editUser_
            // questionExt_
            // userExt_
            rightAlias = rightAlias + "_";

            // t_.id_
            // t_.user_id_
            // t_.edit_user_id_
            String leftId = QueryUtils.camelToUnderscore(rightAlias) + "id_";
            if (one2many || one2one) {
                leftId = "id_";
                if (!rightColumn.isEmpty()) {
                    leftId = QueryUtils.camelToUnderscore(leftColumn) + "_id_";
                }
            }
            String leftAliasId = leftAlias + "." + leftId;
            // user_.id_
            // userExt_.user_id_
            String rightId = "id_";
            if (one2many) {
                if (leftColumn.isEmpty()) {
                    // origAlias为空，使用 leftTable 推断
                    rightId = QueryUtils.camelToUnderscore(leftTable) + "_id_";
                } else {
                    rightId = QueryUtils.camelToUnderscore(rightColumn.isEmpty() ? leftColumn : rightColumn) + "_id_";
                }
            }
            String rightAliasId = rightAlias + "." + rightId;
            String finalRightAlias = rightAlias;
            if (info.getJoinTables().stream().noneMatch(it -> it.getTableAlias().equalsIgnoreCase(finalRightAlias))) {
                String tableName = info.getTablePrefix() + QueryUtils.camelToUnderscore(rightTable);
                info.getJoinTables().add(new QueryInfo.JoinTable(tableName, rightAlias, leftAliasId, rightAliasId));
            }
            leftAlias = rightAlias;
            leftTable = rightTable;
        }
        return leftAlias + "." + QueryUtils.camelToUnderscore(column) + "_";
    }

    private static final String MAIN_TABLE_ALIAS = "t";

    public static final String TABLE_PREFIX = "ujcms_";
    public static final String DISTINCT = "Distinct";
    public static final String ORDER_BY = "OrderBy";
}
