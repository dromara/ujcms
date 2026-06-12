package com.ujcms.common.query;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for QueryInfo
 *
 * @author PONY
 */
class QueryInfoTest {

    @Test
    void testConstructor() {
        QueryInfo info = new QueryInfo("article", "ujcms_");
        assertEquals("article", info.getMainTable());
        assertEquals("ujcms_", info.getTablePrefix());
        assertEquals("ujcms_article", info.getTableName());
        assertFalse(info.isDistinct());
        assertNotNull(info.getJoinTables());
        assertNotNull(info.getWhereConditions());
        assertNotNull(info.getWhereOrAndConditions());
        assertNotNull(info.getOrderByConditions());
    }

    @Test
    void testSetDistinct() {
        QueryInfo info = new QueryInfo("article", "ujcms_");
        assertFalse(info.isDistinct());
        info.setDistinct(true);
        assertTrue(info.isDistinct());
    }

    @Test
    void testSetMainTable() {
        QueryInfo info = new QueryInfo("article", "ujcms_");
        info.setMainTable("user");
        assertEquals("user", info.getMainTable());
        assertEquals("ujcms_user", info.getTableName());
    }

    @Test
    void testSetTablePrefix() {
        QueryInfo info = new QueryInfo("article", "ujcms_");
        info.setTablePrefix("custom_");
        assertEquals("custom_", info.getTablePrefix());
        assertEquals("custom_article", info.getTableName());
    }

    @Test
    void testJoinTables() {
        QueryInfo info = new QueryInfo("article", "ujcms_");
        List<QueryInfo.JoinTable> joinTables = new ArrayList<>();
        joinTables.add(new QueryInfo.JoinTable("ujcms_user", "user_", "t_.user_id_", "user_.id_"));
        info.setJoinTables(joinTables);
        assertEquals(1, info.getJoinTables().size());
        assertEquals("ujcms_user", info.getJoinTables().get(0).getTableName());
    }

    @Test
    void testWhereConditions() {
        QueryInfo info = new QueryInfo("article", "ujcms_");
        List<QueryInfo.WhereCondition> conditions = new ArrayList<>();
        conditions.add(new QueryInfo.WhereCondition("t_.id_", "=", 123));
        info.setWhereConditions(conditions);
        assertEquals(1, info.getWhereConditions().size());
        assertEquals("t_.id_", info.getWhereConditions().get(0).getColumn());
    }

    @Test
    void testWhereOrAndConditions() {
        QueryInfo info = new QueryInfo("article", "ujcms_");
        Map<String, Map<String, List<QueryInfo.WhereCondition>>> orAndConditions = new HashMap<>();
        Map<String, List<QueryInfo.WhereCondition>> group = new HashMap<>();
        List<QueryInfo.WhereCondition> conditions = new ArrayList<>();
        conditions.add(new QueryInfo.WhereCondition("t_.status_", "=", 1));
        group.put("1000", conditions);
        orAndConditions.put("1", group);
        info.setWhereOrAndConditions(orAndConditions);
        assertEquals(1, info.getWhereOrAndConditions().size());
        assertTrue(info.getWhereOrAndConditions().containsKey("1"));
    }

    @Test
    void testOrderByConditions() {
        QueryInfo info = new QueryInfo("article", "ujcms_");
        List<QueryInfo.OrderByCondition> orderByConditions = new ArrayList<>();
        orderByConditions.add(new QueryInfo.OrderByCondition("t_.created_date_", "desc"));
        info.setOrderByConditions(orderByConditions);
        assertEquals(1, info.getOrderByConditions().size());
        assertEquals("t_.created_date_", info.getOrderByConditions().get(0).getColumn());
        assertEquals("desc", info.getOrderByConditions().get(0).getDirection());
    }

    @Test
    void testSetSelectOrderBy() {
        QueryInfo info = new QueryInfo("article", "ujcms_");
        info.setSelectOrderBy("user_.username_");
        assertEquals("user_.username_", info.getSelectOrderBy());
    }

    @Test
    void testSetSelectOrderByNull() {
        QueryInfo info = new QueryInfo("article", "ujcms_");
        info.setSelectOrderBy(null);
        assertNull(info.getSelectOrderBy());
    }

    @Test
    void testWhereConditionConstructor() {
        QueryInfo.WhereCondition condition = new QueryInfo.WhereCondition("t_.id_", "=", 123);
        assertEquals("t_.id_", condition.getColumn());
        assertEquals("=", condition.getOperator());
        assertEquals(123, condition.getValue());
        assertNull(condition.getJsonColumn());
        assertFalse(condition.isArray());
    }

    @Test
    void testWhereConditionWithJsonColumn() {
        QueryInfo.WhereCondition condition = new QueryInfo.WhereCondition(
            "t_.ext_", "customField", "=", false, "value", "String"
        );
        assertEquals("t_.ext_", condition.getColumn());
        assertEquals("customField", condition.getJsonColumn());
        assertEquals("=", condition.getOperator());
        assertEquals("value", condition.getValue());
        assertEquals("String", condition.getType());
    }

    @Test
    void testWhereConditionWithArray() {
        QueryInfo.WhereCondition condition = new QueryInfo.WhereCondition(
            "t_.tags_", null, "IN", true, List.of("a", "b"), "String"
        );
        assertTrue(condition.isArray());
        assertEquals("IN", condition.getOperator());
    }

    @Test
    void testOrderByConditionConstructor() {
        QueryInfo.OrderByCondition condition = new QueryInfo.OrderByCondition("t_.created_date_", "desc");
        assertEquals("t_.created_date_", condition.getColumn());
        assertEquals("desc", condition.getDirection());
        assertNull(condition.getJsonColumn());
        assertNull(condition.getType());
    }

    @Test
    void testOrderByConditionWithJsonColumn() {
        QueryInfo.OrderByCondition condition = new QueryInfo.OrderByCondition(
            "t_.ext_", "desc", "customField", "String"
        );
        assertEquals("t_.ext_", condition.getColumn());
        assertEquals("desc", condition.getDirection());
        assertEquals("customField", condition.getJsonColumn());
        assertEquals("String", condition.getType());
    }

    @Test
    void testJoinTableConstructor() {
        QueryInfo.JoinTable joinTable = new QueryInfo.JoinTable(
            "ujcms_user", "user_", "t_.user_id_", "user_.id_"
        );
        assertEquals("ujcms_user", joinTable.getTableName());
        assertEquals("user_", joinTable.getTableAlias());
        assertEquals("t_.user_id_", joinTable.getLeftId());
        assertEquals("user_.id_", joinTable.getRightId());
    }

    @Test
    void testJoinTableSetters() {
        QueryInfo.JoinTable joinTable = new QueryInfo.JoinTable(
            "ujcms_user", "user_", "t_.user_id_", "user_.id_"
        );
        joinTable.setTableName("ujcms_custom");
        joinTable.setTableAlias("custom_");
        joinTable.setLeftId("t_.custom_id_");
        joinTable.setRightId("custom_.id_");
        assertEquals("ujcms_custom", joinTable.getTableName());
        assertEquals("custom_", joinTable.getTableAlias());
        assertEquals("t_.custom_id_", joinTable.getLeftId());
        assertEquals("custom_.id_", joinTable.getRightId());
    }

    @Test
    void testWhereConditionSetters() {
        QueryInfo.WhereCondition condition = new QueryInfo.WhereCondition("t_.id_", "=", 123);
        condition.setColumn("t_.name_");
        condition.setJsonColumn("customField");
        condition.setOperator("LIKE");
        condition.setArray(true);
        condition.setValue("test");
        condition.setType("String");
        assertEquals("t_.name_", condition.getColumn());
        assertEquals("customField", condition.getJsonColumn());
        assertEquals("LIKE", condition.getOperator());
        assertTrue(condition.isArray());
        assertEquals("test", condition.getValue());
        assertEquals("String", condition.getType());
    }

    @Test
    void testOrderByConditionSetters() {
        QueryInfo.OrderByCondition condition = new QueryInfo.OrderByCondition("t_.id_", "desc");
        condition.setColumn("t_.name_");
        condition.setJsonColumn("customField");
        condition.setDirection("asc");
        condition.setType("String");
        assertEquals("t_.name_", condition.getColumn());
        assertEquals("customField", condition.getJsonColumn());
        assertEquals("asc", condition.getDirection());
        assertEquals("String", condition.getType());
    }

    @Test
    void testWhereConditionToString() {
        QueryInfo.WhereCondition condition = new QueryInfo.WhereCondition(
            "t_.id_", "customField", "=", true, 123, "Int"
        );
        String str = condition.toString();
        assertTrue(str.contains("column='t_.id_'"));
        assertTrue(str.contains("jsonColumn='customField'"));
        assertTrue(str.contains("operator='='"));
        assertTrue(str.contains("array=true"));
    }

    @Test
    void testOrderByConditionToString() {
        QueryInfo.OrderByCondition condition = new QueryInfo.OrderByCondition(
            "t_.id_", "desc", "customField", "String"
        );
        String str = condition.toString();
        assertTrue(str.contains("column='t_.id_'"));
        assertTrue(str.contains("jsonColumn='customField'"));
        assertTrue(str.contains("direction='desc'"));
    }

    @Test
    void testJoinTableToString() {
        QueryInfo.JoinTable joinTable = new QueryInfo.JoinTable(
            "ujcms_user", "user_", "t_.user_id_", "user_.id_"
        );
        String str = joinTable.toString();
        assertTrue(str.contains("tableName='ujcms_user'"));
        assertTrue(str.contains("tableAlias='user_'"));
        assertTrue(str.contains("leftId='t_.user_id_'"));
        assertTrue(str.contains("rightId='user_.id_'"));
    }
}

