package com.ofwise.util.query;

import com.ofwise.util.web.Dates;
import com.ofwise.util.web.Servlets;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.MultiValueMap;
import org.springframework.util.NumberUtils;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.TemporalAccessor;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 查询工具类
 *
 * @author PONY
 */
public class QueryUtils {
    /**
     * 查询前缀
     */
    public static final String QUERY_PREFIX = "Q_";
    /**
     * 自定义字段前缀
     */
    public static final String CUSTOMS_PREFIX = "customs_";

    /**
     * 从 http 请求中获取查询参数
     */
    public static Map<String, Object> getQueryMap(@Nullable String queryString) {
        return getQueryMap(getParams(queryString), QUERY_PREFIX);
    }

    public static Map<String, String> getParams(@Nullable String queryString) {
        return getParams(Servlets.parseQueryString(queryString));
    }

    public static Map<String, String> getParams(MultiValueMap<String, String> paramsMultiMap) {
        return paramsMultiMap.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> joinByComma(entry.getValue())));
    }

    public static Map<String, Object> getQueryMap(Map<String, String> params, String prefix) {
        return params.entrySet().stream().filter(entry -> entry.getKey().startsWith(prefix))
                .collect(Collectors.toMap(entry -> entry.getKey().substring(prefix.length()), Map.Entry::getValue));
    }

    public static Map<String, String> getCustomsQueryMap(Map<String, String> params) {
        return params.entrySet().stream().filter(entry -> entry.getKey().startsWith(CUSTOMS_PREFIX))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static final Pattern QUERY_PATTERN = Pattern.compile("[\\w@-]*");
    private static final Pattern TABLE_PATTERN = Pattern.compile("[\\w]*");
    private static final Pattern FIELD_PATTERN = Pattern.compile("[\\w.]*");
    private static final Pattern ORDER_BY_PATTERN = Pattern.compile("[\\w ,.]*");

    /**
     * 只允许 字母 数字 下划线 中划线 @
     */
    public static void validateQuery(String key) {
        if (!QUERY_PATTERN.matcher(key).matches()) {
            throw new RuntimeException("QueryParser只允许 字母 数字 下划线 中划线 @：" + key);
        }
    }

    /**
     * 只允许 字母 数字 下划线
     */
    public static void validateTable(String table) {
        if (!TABLE_PATTERN.matcher(table).matches()) {
            throw new RuntimeException("QueryParser table 只允许 字母 数字 下划线：" + table);
        }
    }
    /**
     * 只允许 字母 数字 下划线 .
     */
    public static void validateField(String field) {
        if (!FIELD_PATTERN.matcher(field).matches()) {
            throw new RuntimeException("QueryParser field 只允许 字母 数字 下划线 .：" + field);
        }
    }

    /**
     * 只允许 字母 数字 下划线
     */
    public static void validateOrderBy(@Nullable String orderBy) {
        if (StringUtils.isBlank(orderBy)) {
            return;
        }
        if (!ORDER_BY_PATTERN.matcher(orderBy).matches()) {
            throw new RuntimeException("QueryParser orderBy 只允许 字母 数字 空格 下划线 . ,：" + orderBy);
        }
    }


    /**
     * 驼峰名转下划线
     *
     * <p>如：editUser -> edit_user。可加前缀[prefix]，如 prefix = jspbb_，editUser -> jspbb_edit_user
     */
    public static String camelToUnderscore(String name) {
        StringBuilder buff = new StringBuilder();
        for (char c : name.toCharArray()) {
            if (Character.isUpperCase(c)) {
                buff.append("_").append(Character.toLowerCase(c));
            } else {
                buff.append(c);
            }
        }
        return buff.toString();
    }

    /**
     * 与 [StringUtils.join] 不同，null 和 空串 当做不存在，不参与 join
     *
     * @param list 需要 join 的数组，可以为 null
     * @return join 后的字符串。没有内容则为空串
     */
    public static String joinByComma(Collection<String> list) {
        if (list.isEmpty()) {
            return "";
        }
        StringBuilder buff = new StringBuilder();
        for (String s : list) {
            if (s != null && !s.isEmpty()) {
                buff.append(s).append(',');
            }
        }
        // 去除最后一个分隔符
        return (buff.length() > 1) ? buff.substring(0, buff.length() - 1) : "";
    }


    public static final String TYPE_STRING = "String";
    public static final String TYPE_INT = "Int";
    public static final String TYPE_INTEGER = "Integer";
    public static final String TYPE_SHORT = "Short";
    public static final String TYPE_LONG = "Long";
    public static final String TYPE_DOUBLE = "Double";
    public static final String TYPE_BIG_INTEGER = "BigInteger";
    public static final String TYPE_BIG_DECIMAL = "BigDecimal";
    public static final String TYPE_BOOLEAN = "Boolean";
    public static final String TYPE_DATE_TIME = "DateTime";
    public static final String TYPE_DATE = "Date";

    /**
     * 字符串使用 LIKE 可以支持通配符，如`%`
     */
    public static final String OPERATOR_LIKE = "Like";
    /**
     * 包含字符串。前后加通配符，如`%name%`
     */
    public static final String OPERATOR_CONTAINS = "Contains";
    /**
     * 字符串开头。后加通配符，如`name%`
     */
    public static final String OPERATOR_STARTS_WITH = "StartsWith";
    /**
     * 字符串结尾。前加通配符，如`%name`
     */
    public static final String OPERATOR_ENDS_WITH = "EndsWith";

    /**
     * 等于 `=` equals
     */
    public static final String OPERATOR_EQ = "EQ";
    /**
     * 不等于 `!=` not equals
     */
    public static final String OPERATOR_NE = "NE";
    /**
     * 大于 `>` greater than
     */
    public static final String OPERATOR_GT = "GT";
    /**
     * 大于等于 `>=` greater than or equal to
     */
    public static final String OPERATOR_GE = "GE";
    /**
     * 小于 `<` less then
     */
    public static final String OPERATOR_LT = "LT";
    /**
     * 小于等于 `<=` less than or equal to
     */
    public static final String OPERATOR_LE = "LE";

    public static final String OPERATOR_IN = "In";
    public static final String OPERATOR_NOT_IN = "NotIn";
    public static final String OPERATOR_IS_NULL = "IsNull";
    public static final String OPERATOR_IS_NOT_NULL = "IsNotNull";

    /**
     * 支持Like, Contain, StartWith, EndWith, In, NotIn, IsNull, IsNotNull, EQ, NE, GT, LT, GE, LE
     */
    public static String getOperator(String s) {
        switch (s) {
            case OPERATOR_LIKE:
            case OPERATOR_CONTAINS:
            case OPERATOR_STARTS_WITH:
            case OPERATOR_ENDS_WITH:
                return "LIKE";
            case OPERATOR_IN:
                return "IN";
            case OPERATOR_NOT_IN:
                return "NOT IN";
            case OPERATOR_IS_NULL:
                return "IS NULL";
            case OPERATOR_IS_NOT_NULL:
                return "IS NOT NULL";
            case OPERATOR_EQ:
                return "=";
            case OPERATOR_NE:
                return "<>";
            case OPERATOR_GT:
                return ">";
            case OPERATOR_GE:
                return ">=";
            case OPERATOR_LT:
                return "<";
            case OPERATOR_LE:
                return "<=";
            default:
                throw new RuntimeException("QueryParser operator '$s' not supported. Support: Like, Contain, " +
                        "StartWith, EndWith, In, NotIn, IsNull, IsNotNull, EQ, NE, GT, LT, GE, LE");
        }
    }

    /**
     * 支持 String, Int(Integer), Long, Double, Boolean, DateTime, Date, BigDecimal, BigInteger
     */
    @Nullable
    public static Object getValue(String type, @Nullable Object obj, String operator) {
        // IS NULL 和 IS NOT NULL 不需要 value，直接返回空串
        if (operator.equalsIgnoreCase(OPERATOR_IS_NULL) || operator.equalsIgnoreCase(OPERATOR_IS_NOT_NULL)) {
            return null;
        }
        // value 为空串则返回 null，将被忽略
        // value 为空串或空格串 且 type 非字符串 则返回 null，将被忽略
        // if (value.isEmpty()) {
        //     return null;
        // }
        switch (type) {
            case TYPE_STRING:
                switch (operator) {
                    case OPERATOR_IN:
                    case OPERATOR_NOT_IN:
                        return parseStrings(obj);
                    case OPERATOR_CONTAINS:
                        return "%" + obj + "%";
                    case OPERATOR_STARTS_WITH:
                        return obj + "%";
                    case OPERATOR_ENDS_WITH:
                        return "%" + obj;
                    default:
                        return obj;
                }
            case TYPE_SHORT:
                if (operator.equalsIgnoreCase(OPERATOR_IN) || operator.equalsIgnoreCase(OPERATOR_NOT_IN)) {
                    return parseShorts(obj);
                }
                return parseShort(obj);
            case TYPE_INT:
            case TYPE_INTEGER:
                if (operator.equalsIgnoreCase(OPERATOR_IN) || operator.equalsIgnoreCase(OPERATOR_NOT_IN)) {
                    return parseIntegers(obj);
                }
                return parseInteger(obj);
            case TYPE_LONG:
                if (operator.equalsIgnoreCase(OPERATOR_IN) || operator.equalsIgnoreCase(OPERATOR_NOT_IN)) {
                    return parseLongs(obj);
                }
                return parseLong(obj);
            case TYPE_DOUBLE:
                if (operator.equalsIgnoreCase(OPERATOR_IN) || operator.equalsIgnoreCase(OPERATOR_NOT_IN)) {
                    return parseDoubles(obj);
                }
                return parseDouble(obj);
            case TYPE_BIG_INTEGER:
                if (operator.equalsIgnoreCase(OPERATOR_IN) || operator.equalsIgnoreCase(OPERATOR_NOT_IN)) {
                    return parseBigIntegers(obj);
                }
                return parseBigInteger(obj);
            case TYPE_BIG_DECIMAL:
                if (operator.equalsIgnoreCase(OPERATOR_IN) || operator.equalsIgnoreCase(OPERATOR_NOT_IN)) {
                    return parseBigDecimals(obj);
                }
                return parseBigDecimal(obj);
            case TYPE_BOOLEAN:
                Boolean bool = parseBoolean(obj);
                if (bool == null) {
                    return null;
                }
                // 数据库中使用数值类型（如：tinyint）代替布尔类型
                return bool ? 1 : 0;
            case TYPE_DATE_TIME:
                return parseDate(obj);
            case TYPE_DATE:
                OffsetDateTime date = parseDate(obj);
                // 日期类型判断小于时，需要加一天。比如大于 2008-10-01 小于 2008-10-02，实际上应该小于 2008-10-03 00:00:00
                if (date != null && StringUtils.equalsAnyIgnoreCase(OPERATOR_LE, OPERATOR_LT)) {
                    date = date.plusDays(1);
                }
                return date;
            default:
                throw new RuntimeException("QueryParser type '" + type + "' not supported. Support: " +
                        "String, Int(Integer), Long, Double, Boolean, DateTime, Date, BigDecimal, BigInteger");
        }
    }

    @Nullable
    public static Boolean parseBoolean(@Nullable Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof Boolean) {
            return (Boolean) obj;
        }
        if (obj instanceof String) {
            return Boolean.valueOf((String) obj);
        }
        throw new RuntimeException("Cannot parse to Boolean: " + obj);
    }

    @Nullable
    public static OffsetDateTime parseDate(@Nullable Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof TemporalAccessor) {
            return Dates.from((TemporalAccessor) obj);
        }
        if (obj instanceof Date) {
            return Dates.ofDate((Date) obj);
        }
        if (obj instanceof String) {
            return Dates.parse((String) obj);
        }
        throw new RuntimeException("Cannot parse to OffsetDateTime: " + obj);
    }

    @Nullable
    public static String parseString(@Nullable Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof String) {
            return (String) obj;
        }
        if (obj instanceof Number) {
            return obj.toString();
        }
        if (obj instanceof TemporalAccessor) {
            return Instant.from((TemporalAccessor) obj).toString();
        }
        if (obj instanceof Date) {
            return ((Date) obj).toInstant().toString();
        }
        if (obj instanceof Collection) {
            return StringUtils.joinWith(",", ((Collection<?>) obj).toArray());
        }
        if (obj.getClass().isArray()) {
            return StringUtils.joinWith(",", obj);
        }
        throw new RuntimeException("Cannot parse to String: " + obj);
    }

    @Nullable
    public static Collection<String> parseStrings(@Nullable Object obj) {
        return parseList(obj, String.class, Object::toString);
    }

    @Nullable
    public static Integer parseInteger(@Nullable Object obj) {
        return parseNumber(obj, Integer.class);
    }

    @Nullable
    public static Collection<Integer> parseIntegers(@Nullable Object obj) {
        return parseList(obj, Integer.class, QueryUtils::parseInteger);
    }

    @Nullable
    public static Short parseShort(@Nullable Object value) {
        return parseNumber(value, Short.class);
    }

    @Nullable
    public static Collection<Short> parseShorts(@Nullable Object obj) {
        return parseList(obj, Short.class, QueryUtils::parseShort);
    }

    @Nullable
    public static Long parseLong(@Nullable Object obj) {
        return parseNumber(obj, Long.class);
    }

    @Nullable
    public static Collection<Long> parseLongs(@Nullable Object obj) {
        return parseList(obj, Long.class, QueryUtils::parseLong);
    }

    @Nullable
    public static Double parseDouble(@Nullable Object obj) {
        return parseNumber(obj, Double.class);
    }

    @Nullable
    public static Collection<Double> parseDoubles(@Nullable Object obj) {
        return parseList(obj, Double.class, QueryUtils::parseDouble);
    }

    @Nullable
    public static BigInteger parseBigInteger(@Nullable Object obj) {
        return parseNumber(obj, BigInteger.class);
    }

    @Nullable
    public static Collection<BigInteger> parseBigIntegers(@Nullable Object obj) {
        return parseList(obj, BigInteger.class, QueryUtils::parseBigInteger);
    }

    @Nullable
    public static BigDecimal parseBigDecimal(@Nullable Object obj) {
        return parseNumber(obj, BigDecimal.class);
    }

    @Nullable
    public static Collection<BigDecimal> parseBigDecimals(@Nullable Object obj) {
        return parseList(obj, BigDecimal.class, QueryUtils::parseBigDecimal);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    public static <T extends Number> T parseNumber(@Nullable Object obj, Class<T> targetClass) {
        if (obj == null) {
            return null;
        }
        Class<?> clazz = obj.getClass();
        if (clazz.equals(targetClass)) {
            return (T) obj;
        }
        if (obj instanceof Number) {
            return NumberUtils.convertNumberToTargetClass((Number) obj, targetClass);
        }
        if (obj instanceof String) {
            return NumberUtils.parseNumber((String) obj, targetClass);
        }
        throw new RuntimeException("Cannot parse to Number: " + obj);
    }

    @Nullable
    public static <T extends Number> Collection<T> parseNumbers(@Nullable Object obj, Class<T> targetClass) {
        return parseList(obj, targetClass, it -> parseNumber(it, targetClass));
    }

    @SuppressWarnings("unchecked")
    @Nullable
    public static <T> Collection<T> parseList(@Nullable Object obj, Class<T> targetClass, Function<Object, T> parser) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof Collection<?>) {
            if (targetClass.equals(getGenericsClass(obj))) {
                return (Collection<T>) obj;
            }
            return ((Collection<?>) obj).stream().map(parser).collect(Collectors.toList());
        }
        if (targetClass.equals(obj.getClass())) {
            return Arrays.asList((T[]) obj);
        }
        if (obj instanceof String) {
            return Arrays.stream(StringUtils.split((String) obj, ",")).map(parser).collect(Collectors.toList());
        }
        throw new RuntimeException("Cannot parse to List: " + obj);
    }

    @Nullable
    public static Class<?> getGenericsClass(Object obj) {
        Type type = obj.getClass().getGenericSuperclass();
        if (type instanceof ParameterizedType) {
            Type[] actualTypes = ((ParameterizedType) type).getActualTypeArguments();
            if (actualTypes.length > 0) {
                if (actualTypes[0] instanceof Class<?>) {
                    return (Class<?>) actualTypes[0];
                }
            }
        }
        return null;
    }

    /**
     * 工具类不需要实例化
     */
    private QueryUtils() {
    }
}
