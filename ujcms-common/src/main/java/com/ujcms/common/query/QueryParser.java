package com.ujcms.common.query;

import static com.ujcms.common.query.QueryUtils.COMMA;
import static com.ujcms.common.query.QueryUtils.DASH;
import static com.ujcms.common.query.QueryUtils.DIRECTION_ASC;
import static com.ujcms.common.query.QueryUtils.DIRECTION_DESC;
import static com.ujcms.common.query.QueryUtils.DOLLAR;
import static com.ujcms.common.query.QueryUtils.POINT;
import static com.ujcms.common.query.QueryUtils.UNDERLINE;
import static com.ujcms.common.query.QueryUtils.camelToUnderscore;
import static com.ujcms.common.query.QueryUtils.underscoreToCamel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.jspecify.annotations.Nullable;

import com.ujcms.common.web.exception.Http400Exception;

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

    /**
     * 前台接口不允许关联的表（前缀匹配，不区分大小写）。防止通过关联用户、组织、角色等表，以查询条件或排序的方式探测敏感数据
     */
    private static final List<String> PUBLIC_DENIED_TABLES = List.of(
            "user", "org", "role", "group", "config", "loginlog", "operationlog", "shortmessage");
    /**
     * 前台接口不允许查询的字段（包含匹配，不区分大小写）
     */
    private static final List<String> PUBLIC_DENIED_COLUMN_PARTS = List.of("password", "salt", "secret", "token");
    /**
     * 前台接口不允许查询的字段（完全匹配，不区分大小写）
     */
    private static final List<String> PUBLIC_DENIED_COLUMNS = List.of(
            "ip", "mobile", "phone", "email", "address", "contact", "realname");
    /**
     * 前台接口不允许查询、排序的大字段（表名 → 字段，均为小写）。它们没有索引，LIKE 或排序都是全表扫描，
     * 匿名请求不应当能触发：文章正文、Markdown 源、大字段型自定义字段（clobsJson，含其下的 {@code $字段}）。
     * 按表限定，不波及别的表里同名的字段
     */
    private static final Map<String, List<String>> PUBLIC_DENIED_TABLE_COLUMNS = Map.of(
            "articleext", List.of("text", "markdown", "clobsjson"));

    /**
     * 校验前台接口的查询键。前台接口的查询参数由匿名用户传入，不允许关联敏感表或查询敏感字段
     *
     * @param key 查询键，格式如：EQ_price_Int, Like_user-username, In_1_status
     */
    public static void validatePublicKey(String key) {
        if (key.equalsIgnoreCase(DISTINCT) || key.equals(ORDER_BY)) {
            return;
        }
        GroupInfo groupInfo = extractGroupInfo(parseQueryKey(key).columnPart());
        validateRequestGroup(groupInfo, key);
        validatePublicColumnPart(groupInfo.columnPart(), key);
    }

    /**
     * 校验前台接口的排序参数。按敏感字段排序同样可以探测数据
     *
     * @param orderBy 排序字符串，如：publishDate_desc,id_desc
     */
    public static void validatePublicOrderBy(@Nullable String orderBy) {
        if (orderBy == null) {
            return;
        }
        for (String item : StringUtils.split(orderBy, COMMA)) {
            item = item.trim();
            QueryUtils.validateQuery(item);
            validatePublicColumnPart(parseOrderByParts(item).columnPart(), item);
        }
    }

    /**
     * 后台接口不允许查询、排序的字段（列名，不区分大小写）：凭证类的列，任何表上都不行
     */
    private static final List<String> BACKEND_DENIED_COLUMN_PARTS = List.of("password", "secret");
    private static final List<String> BACKEND_DENIED_COLUMNS = List.of("salt", "token", "aeskey");
    private static final String BACKEND_DENIED_COLUMN_SUFFIX = "tokenhash";
    /**
     * 后台接口不允许关联的表（完全匹配，不区分大小写）：全局配置表的各列都是设置与密钥
     */
    private static final String BACKEND_DENIED_TABLE = "config";

    /**
     * 校验后台接口的查询参数（docs/spec/article-data-permission/plan.md D19）：操作员传入的条件与排序不得指向凭证类的列
     * （主表或关联表上的都不行），也不得关联全局配置表。这是所有后台列表接口共同的下限，各接口可再按
     * {@link #validateJoins} 收窄。不符合要求则抛出 400 异常
     *
     * @param key   查询键（已去除 {@code Q_} 前缀），如：EQ_price_Int, Like_user-username, OrderBy
     * @param value 查询值。只在键为 {@code OrderBy} 时看它
     */
    public static void validateBackendKey(String key, @Nullable Object value) {
        if (key.equalsIgnoreCase(DISTINCT)) {
            return;
        }
        if (key.equals(ORDER_BY)) {
            if (value instanceof String orderBy) {
                for (String item : StringUtils.split(orderBy, COMMA)) {
                    item = item.trim();
                    QueryUtils.validateQuery(item);
                    validateBackendColumnPart(stripGroup(parseOrderByParts(item).columnPart()), item);
                }
            }
            return;
        }
        GroupInfo groupInfo = extractGroupInfo(parseQueryKey(key).columnPart());
        validateRequestGroup(groupInfo, key);
        validateBackendColumnPart(groupInfo.columnPart(), key);
    }

    /**
     * 服务端条件组的前缀（docs/spec/article-data-permission/plan.md D26）：代码写入查询表的 OR 组——站点范围
     * {@code 0scope}（本站或全局）、上级或自身 {@code 0tree}、关键字 {@code 0q}、发布期 {@code 0online} / {@code 0start} /
     * {@code 0end}——组名以 {@code 0} 开头；请求传入的组只许 {@code 1} 起的正整数（子组为数字），两者永不同组。
     * 否则请求可以把自己的条件并入服务端的 OR 组（{@code ?Q_GT_1_id_Long=0}），把“本站或全局”扩成“本站或全局或任意”
     */
    public static final String SERVER_GROUP_PREFIX = "0";
    private static final Pattern REQUEST_GROUP_PATTERN = Pattern.compile("[1-9]\\d*");
    private static final Pattern REQUEST_SUB_GROUP_PATTERN = Pattern.compile("\\d+");

    /**
     * 校验请求传入的分组：主组须是 1 起的正整数，子组须是数字；以 {@value #SERVER_GROUP_PREFIX} 开头的组名留给服务端。
     * 两个请求入口（{@link #validateBackendKey}、{@link #validatePublicKey}）都经此处；服务端写入的键不经校验
     */
    private static void validateRequestGroup(GroupInfo groupInfo, String raw) {
        String group = groupInfo.group();
        if (group == null) {
            return;
        }
        String subGroup = groupInfo.subGroup();
        if (!REQUEST_GROUP_PATTERN.matcher(group).matches()
                || (subGroup != null && !REQUEST_SUB_GROUP_PATTERN.matcher(subGroup).matches())) {
            throw new Http400Exception("Query not allowed: " + raw);
        }
    }

    private static void validateBackendColumnPart(String columnPart, String raw) {
        String[] parts = splitPath(columnPart, raw);
        for (int i = 0; i < parts.length - 1; i += 1) {
            for (String table : StringUtils.split(parts[i], '@')) {
                if (BACKEND_DENIED_TABLE.equalsIgnoreCase(table)) {
                    throw new Http400Exception("Query not allowed: " + raw);
                }
            }
        }
        String column = parts[parts.length - 1].toLowerCase();
        if (BACKEND_DENIED_COLUMNS.contains(column) || column.endsWith(BACKEND_DENIED_COLUMN_SUFFIX)
                || BACKEND_DENIED_COLUMN_PARTS.stream().anyMatch(column::contains)) {
            throw new Http400Exception("Query not allowed: " + raw);
        }
    }

    /**
     * 许可路径的通配后缀：{@code @articleExt-*} 许可该关联下的任何列。只该用于主表行的一对一扩展表——那是同一行拆开存放，
     * 凭证列的下限（{@link #validateBackendKey}）仍然适用
     */
    public static final String ANY_COLUMN = "*";

    /**
     * 校验请求传入的查询只关联许可的字段（docs/spec/article-data-permission/plan.md D18、D24）：一个列表接口只回答它展示的东西。
     * 主表自身的列（含 JSON 自定义字段）无需列出，总是许可；带关联的路径须在许可之列。条件与排序都看。
     * 不符合要求则抛出 400 异常
     *
     * @param queryMap     查询参数（已去除 {@code Q_} 前缀）
     * @param allowedPaths 许可的关联路径，如 {@code user-username}、{@code @formExt-clobsJson}（JSON 字段名不计）、
     *                     {@code @userExt-*}（该关联下的任何列）。空集合即只许主表自身的列
     */
    public static void validateJoins(Map<String, Object> queryMap, Collection<String> allowedPaths) {
        for (Map.Entry<String, Object> entry : queryMap.entrySet()) {
            String key = entry.getKey();
            if (key.equalsIgnoreCase(DISTINCT)) {
                continue;
            }
            if (key.equals(ORDER_BY)) {
                if (entry.getValue() instanceof String orderBy) {
                    for (String item : StringUtils.split(orderBy, COMMA)) {
                        item = item.trim();
                        QueryUtils.validateQuery(item);
                        requireAllowedPath(stripGroup(parseOrderByParts(item).columnPart()), allowedPaths, item);
                    }
                }
                continue;
            }
            requireAllowedPath(extractGroupInfo(parseQueryKey(key).columnPart()).columnPart(), allowedPaths, key);
        }
    }

    private static void requireAllowedPath(String columnPart, Collection<String> allowedPaths, String raw) {
        String[] parts = splitPath(columnPart, raw);
        if (parts.length <= 1) {
            return;
        }
        if (allowedPaths.contains(String.join(DASH, parts))) {
            return;
        }
        parts[parts.length - 1] = ANY_COLUMN;
        if (allowedPaths.contains(String.join(DASH, parts))) {
            return;
        }
        throw new Http400Exception("Query not allowed: " + raw);
    }

    /**
     * 去除分组标识（与 {@link #parseJoinTable} 同一规则：第一个下划线之前的部分）
     */
    private static String stripGroup(String columnPart) {
        int underIndex = columnPart.indexOf(UNDERLINE);
        return underIndex >= 0 ? columnPart.substring(underIndex + 1) : columnPart;
    }

    /**
     * 按关联拆分路径（{@code editUser@user-@userExt-username} → {@code [editUser@user, @userExt, username]}），
     * 去除末段的 JSON 字段名：JSON 字段是自定义字段，不参与校验。JSON 字段名只能在末段（解析器也是如此：
     * 关联段里的 {@code $} 不是合法的表名），出现在别处即拒绝
     */
    private static String[] splitPath(String columnPart, String raw) {
        String[] parts = columnPart.split(DASH);
        for (int i = 0; i < parts.length - 1; i += 1) {
            if (parts[i].contains(DOLLAR)) {
                throw new Http400Exception("Query not allowed: " + raw);
            }
        }
        String column = parts[parts.length - 1];
        int jsonIndex = column.indexOf(DOLLAR);
        if (jsonIndex >= 0) {
            parts[parts.length - 1] = column.substring(0, jsonIndex);
        }
        return parts;
    }

    private static void validatePublicColumnPart(String columnPart, String raw) {
        // 去除JSON字段。JSON字段为自定义字段，无需校验
        int jsonIndex = columnPart.indexOf(DOLLAR);
        if (jsonIndex >= 0) {
            columnPart = columnPart.substring(0, jsonIndex);
        }
        // editUser@user-@userExt-username => [editUser@user | @userExt | username]
        String[] parts = columnPart.split(DASH);
        for (int i = 0; i < parts.length - 1; i += 1) {
            // editUser@user, @userExt, org@OrgTree@descendant
            for (String table : StringUtils.split(parts[i], '@')) {
                String lowerTable = table.toLowerCase();
                if (PUBLIC_DENIED_TABLES.stream().anyMatch(lowerTable::startsWith)) {
                    throw new Http400Exception("Query not allowed: " + raw);
                }
            }
        }
        String column = parts[parts.length - 1].toLowerCase();
        if (PUBLIC_DENIED_COLUMNS.contains(column)
                || PUBLIC_DENIED_COLUMN_PARTS.stream().anyMatch(column::contains)) {
            throw new Http400Exception("Query not allowed: " + raw);
        }
        // 字段所在的表：路径里最后一段关联的最后一个表名，如 @articleExt-text => articleExt
        if (parts.length > 1) {
            String[] tables = StringUtils.split(parts[parts.length - 2], '@');
            String table = tables.length > 0 ? tables[tables.length - 1].toLowerCase() : "";
            if (PUBLIC_DENIED_TABLE_COLUMNS.getOrDefault(table, List.of()).contains(column)) {
                throw new Http400Exception("Query not allowed: " + raw);
            }
        }
    }

    private static boolean isContinueKey(QueryInfo info, String key, @Nullable Object value) {
        if (key.equalsIgnoreCase(DISTINCT)) {
            // Distinct=false 就是不去重：以前只看键不看值，传 false 也开启了 DISTINCT
            info.setDistinct(!Boolean.FALSE.equals(value)
                    && !(value instanceof String string && "false".equalsIgnoreCase(string.trim())));
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
