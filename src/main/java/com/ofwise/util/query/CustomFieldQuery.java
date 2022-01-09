package com.ofwise.util.query;

import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 自定义字段表的查询
 * <p>
 * 操作符支持：EQ, NE, GT, GE, LT, LE, Like, Contains, StartsWith, EndsWith
 * <p>
 * 类型支持：String, Integer(Int), Long, BigDecimal, Date, DateTime, Boolean
 *
 * @author PONY
 */
public class CustomFieldQuery {
    @Nullable
    public static List<QueryInfo.WhereCondition> parse(@Nullable Map<String, String> params) {
        if (params == null) {
            return null;
        }
        List<QueryInfo.WhereCondition> list = new ArrayList<>();
        params.forEach((key, value) -> {
            // EQ_name
            // Contains_name
            String[] parts = StringUtils.split(key, "_");
            int minLength = 2;
            if (parts.length < minLength) {
                throw new IllegalArgumentException("Illegal customs field query key format: " + key);
            }
            String operator = parts[0];
            String name = parts[1];
            String type = "String";
            list.add(new QueryInfo.WhereCondition(name, QueryUtils.getOperator(operator), QueryUtils.getValue(type, value, operator)));
        });
        return list;
    }

    /**
     * 工具类不需要实例化
     */
    private CustomFieldQuery() {
    }
}
