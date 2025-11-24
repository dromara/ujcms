package com.ujcms.commons.query;

import static com.ujcms.commons.query.QueryUtils.COMMA;
import static com.ujcms.commons.query.QueryUtils.DASH;
import static com.ujcms.commons.query.QueryUtils.DIRECTION_ASC;
import static com.ujcms.commons.query.QueryUtils.DIRECTION_DESC;
import static com.ujcms.commons.query.QueryUtils.DOLLAR;
import static com.ujcms.commons.query.QueryUtils.POINT;
import static com.ujcms.commons.query.QueryUtils.UNDERLINE;
import static com.ujcms.commons.query.QueryUtils.camelToUnderscore;
import static com.ujcms.commons.query.QueryUtils.underscoreToCamel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.lang.Nullable;

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
        boolean nullOrBlank = value == null || (value instanceof String string && StringUtils.isBlank(string));
        if (!Strings.CS.startsWithAny(key, ORDER_BY, QueryUtils.OPERATOR_IS_NULL, QueryUtils.OPERATOR_IS_NOT_NULL)
                && nullOrBlank) {
            return true;
        }
        if (key.equals(ORDER_BY)) {
            if (value instanceof String string) {
                parseOrderBy(info, string);
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
        
        int nextSubGroup = 1000;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            if (isContinueKey(info, key, value)) {
                continue;
            }
            
            QueryKeyParts keyParts = parseQueryKey(key);
            parseWhereCondition(info, keyParts.columnPart(), keyParts.operator(), 
                              value, keyParts.type(), nextSubGroup);
            nextSubGroup += 1;
        }
        return info;
    }

    /**
     * 解析查询键，提取操作符、列部分和类型
     * 
     * @param key 查询键，格式如：EQ_price_Int, Like_username, In_1_status
     * @return 解析后的键部分
     */
    private static QueryKeyParts parseQueryKey(String key) {
        QueryUtils.validateQuery(key);
        
        int index = key.indexOf(UNDERLINE);
        if (index == -1) {
            throw new IllegalArgumentException("Illegal query key format: " + key);
        }
        
        // 获取操作符：EQ, Like, In 等
        String operator = key.substring(0, index);
        
        // 获取列部分和类型：price_Int, username, 1_questionExt-title 等
        String remaining = key.substring(index + 1);
        if (remaining.isEmpty()) {
            throw new IllegalArgumentException("Illegal query key format: " + key);
        }
        
        // 查找类型分隔符（第二个下划线）
        // 如果第一个字符是数字，需要跳过第一个下划线（分组标识）
        int typeIndex = remaining.indexOf(UNDERLINE, 
            Character.isDigit(remaining.charAt(0)) ? remaining.indexOf(UNDERLINE) + 1 : 0);
        
        String columnPart;
        String type = QueryUtils.TYPE_STRING;
        
        if (typeIndex == -1) {
            columnPart = remaining;
        } else {
            columnPart = remaining.substring(0, typeIndex);
            type = remaining.substring(typeIndex + 1);
        }
        
        return new QueryKeyParts(operator, columnPart, type);
    }

    /**
     * 查询键的组成部分
     */
    private record QueryKeyParts(String operator, String columnPart, String type) {
    }

    private static void parseOrderBy(QueryInfo info, @Nullable String rawOrderBy) {
        if (rawOrderBy == null) {
            return;
        }
        
        StringBuilder selectBuff = new StringBuilder();
        List<QueryInfo.OrderByCondition> orderByConditions = new ArrayList<>();
        
        for (String orderBy : StringUtils.split(rawOrderBy, COMMA)) {
            orderBy = orderBy.trim();
            QueryUtils.validateQuery(orderBy);
            
            OrderByParts parts = parseOrderByParts(orderBy);
            String column = parseJoinTable(info, parts.columnPart());
            ColumnInfo columnInfo = extractJsonColumn(column);
            
            orderByConditions.add(new QueryInfo.OrderByCondition(
                columnInfo.column(), 
                parts.direction(), 
                columnInfo.jsonColumn(), 
                parts.type()
            ));
            
            if (!columnInfo.column().startsWith(MAIN_TABLE_ALIAS + POINT)) {
                selectBuff.append(COMMA).append(columnInfo.column());
            }
        }
        
        info.setOrderByConditions(orderByConditions);
        info.setSelectOrderBy(selectBuff.length() > 0 ? selectBuff.toString() : null);
    }

    /**
     * 解析OrderBy部分，提取列名、方向和类型
     * 
     * @param orderBy OrderBy字符串，如：username_desc, created_desc, username_Int
     * @return 解析后的OrderBy部分
     */
    private static OrderByParts parseOrderByParts(String orderBy) {
        String direction = null;
        String type = null;
        String columnPart = orderBy;
        
        int index = orderBy.lastIndexOf(UNDERLINE);
        if (index != -1) {
            String suffix = orderBy.substring(index + 1);
            columnPart = orderBy.substring(0, index);
            
            // 检查是否是方向（asc/desc）
            if (DIRECTION_ASC.equals(suffix) || DIRECTION_DESC.equals(suffix)) {
                direction = suffix;
                // 检查是否还有类型
                index = columnPart.indexOf(UNDERLINE);
                if (index != -1) {
                    type = columnPart.substring(index + 1);
                    columnPart = columnPart.substring(0, index);
                }
            } else {
                // 不是方向，则是类型
                type = suffix;
            }
        }
        
        return new OrderByParts(columnPart, direction, type);
    }

    /**
     * OrderBy的组成部分
     */
    private record OrderByParts(String columnPart, @Nullable String direction, @Nullable String type) {
    }

    private static void parseWhereCondition(QueryInfo info, String columnPart, String operator,
                                            @Nullable Object obj, String type, int nextSubGroup) {
        // 提取分组信息（如果有）
        GroupInfo groupInfo = extractGroupInfo(columnPart);
        String actualColumnPart = groupInfo.columnPart();
        
        // 解析表连接和列
        String column = parseJoinTable(info, actualColumnPart);
        ColumnInfo columnInfo = extractJsonColumn(column);
        
        // 转换值
        Object value = QueryUtils.getValue(type, obj, operator);
        
        // 创建条件
        QueryInfo.WhereCondition condition = new QueryInfo.WhereCondition(
            columnInfo.column(), 
            columnInfo.jsonColumn(),
            QueryUtils.getOperator(operator), 
            QueryUtils.isArrayQuery(operator), 
            value, 
            type
        );
        
        // 添加到相应的条件列表
        if (groupInfo.hasGroup()) {
            addGroupedCondition(info, condition, groupInfo.group(), groupInfo.subGroup(), nextSubGroup);
        } else {
            info.getWhereConditions().add(condition);
        }
    }

    /**
     * 提取分组信息
     * 
     * @param columnPart 列部分，可能包含分组标识，如：1_questionExt-title, 1-1_username
     * @return 分组信息和实际的列部分
     */
    private static GroupInfo extractGroupInfo(String columnPart) {
        int groupIndex = columnPart.indexOf(UNDERLINE);
        if (groupIndex < 0) {
            return new GroupInfo(null, null, columnPart);
        }
        
        String group = columnPart.substring(0, groupIndex);
        String actualColumnPart = columnPart.substring(groupIndex + 1);
        
        // 检查是否有子组（用-分隔）
        int subGroupIndex = group.indexOf(DASH);
        String mainGroup = group;
        String subGroup = null;
        
        if (subGroupIndex >= 0) {
            mainGroup = group.substring(0, subGroupIndex);
            subGroup = group.substring(subGroupIndex + 1);
        }
        
        return new GroupInfo(mainGroup, subGroup, actualColumnPart);
    }

    /**
     * 提取JSON列信息
     * 
     * @param column 列名，可能包含JSON路径，如：t_.mains_json_$abc
     * @return 列信息和JSON列名
     */
    private static ColumnInfo extractJsonColumn(String column) {
        int jsonIndex = column.indexOf(DOLLAR);
        if (jsonIndex < 0) {
            return new ColumnInfo(column, null);
        }
        
        String jsonColumn = underscoreToCamel(column.substring(jsonIndex + 1));
        String actualColumn = column.substring(0, jsonIndex);
        return new ColumnInfo(actualColumn, jsonColumn);
    }

    /**
     * 添加分组条件
     */
    private static void addGroupedCondition(QueryInfo info, QueryInfo.WhereCondition condition,
                                           @Nullable String group, @Nullable String subGroup, int defaultSubGroup) {
        if (group == null) {
            throw new IllegalArgumentException("Group cannot be null when adding grouped condition");
        }
        String actualSubGroup = subGroup != null ? subGroup : String.valueOf(defaultSubGroup);
        
        Map<String, List<QueryInfo.WhereCondition>> orConditions =
            info.getWhereOrAndConditions().computeIfAbsent(group, k -> new HashMap<>(16));
        List<QueryInfo.WhereCondition> orAndConditions =
            orConditions.computeIfAbsent(actualSubGroup, k -> new ArrayList<>());
        orAndConditions.add(condition);
    }

    /**
     * 分组信息
     */
    private record GroupInfo(@Nullable String group, @Nullable String subGroup, String columnPart) {
        boolean hasGroup() {
            return group != null;
        }
    }

    /**
     * 列信息（包含JSON列）
     */
    private record ColumnInfo(String column, @Nullable String jsonColumn) {
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
     * 工具类不需要实例化
     */
    private QueryParser() {
        throw new IllegalStateException("Utility class");
    }
}
