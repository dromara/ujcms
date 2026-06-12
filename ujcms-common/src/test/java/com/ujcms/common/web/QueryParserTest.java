package com.ujcms.common.web;

import com.ujcms.common.query.QueryInfo;
import com.ujcms.common.query.QueryParser;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

class QueryParserTest {

    @Test
    void parseJoinTable() {
        String columnPart = "EQ_user-user@UserRole-editUser@user-@userExt-ancestor@ChannelTree-roleId";
        String orderBy = "@articleExt-title,order,id_desc, publicDate";
        String table = "article";
        Map<String, Object> params = new HashMap<>();
        params.put("EQ_1-1_type@dictType-alias", "123");
        params.put("EQ_1-2_type@dictType-scope", "123");
        params.put("EQ_2-1_type@dictType-alias", "123");
        params.put("EQ_2-2_type@dictType-siteId", "123");
        params.put("Contains_name", "123");
        params.put(columnPart, "123");
        params.put("OrderBy", orderBy);
        System.out.println("column part: " + columnPart + "; table: " + table);
        QueryInfo queryInfo = QueryParser.parse(params, table);
        if (queryInfo == null) return;
        for (QueryInfo.JoinTable joinTable : queryInfo.getJoinTables()) {
            System.out.println(joinTable);
        }
        System.out.println("orderBy: " + queryInfo.getOrderByConditions());
        System.out.println("whereCondition: " + queryInfo.getWhereConditions());
        // System.out.println("whereOrConditions: " + queryInfo.getWhereOrConditions());
        System.out.println("whereOrAndConditions: " + queryInfo.getWhereOrAndConditions());
    }
}