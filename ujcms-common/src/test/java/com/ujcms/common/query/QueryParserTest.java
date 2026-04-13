package com.ujcms.common.query;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for QueryParser
 *
 * @author PONY
 */
class QueryParserTest {

    @Test
    void testParseWithNullParams() {
        QueryInfo result = QueryParser.parse(null, "article");
        assertNull(result);
    }

    @Test
    void testParseWithEmptyParams() {
        Map<String, Object> params = new HashMap<>();
        QueryInfo result = QueryParser.parse(params, "article");
        assertNotNull(result);
        assertEquals("article", result.getMainTable());
        assertEquals("ujcms_", result.getTablePrefix());
        assertFalse(result.isDistinct());
        assertTrue(result.getWhereConditions().isEmpty());
        assertTrue(result.getJoinTables().isEmpty());
    }

    @Test
    void testParseDistinct() {
        Map<String, Object> params = new HashMap<>();
        params.put("Distinct", "true");
        QueryInfo result = QueryParser.parse(params, "article");
        assertNotNull(result);
        assertTrue(result.isDistinct());
    }

    @Test
    void testParseOrderBy() {
        Map<String, Object> params = new HashMap<>();
        params.put("OrderBy", "createdDate_desc,id_asc");
        QueryInfo result = QueryParser.parse(params, "article");
        assertNotNull(result);
        assertEquals(2, result.getOrderByConditions().size());
        assertEquals("t.created_date_", result.getOrderByConditions().get(0).getColumn());
        assertEquals("desc", result.getOrderByConditions().get(0).getDirection());
        assertEquals("t.id_", result.getOrderByConditions().get(1).getColumn());
        assertEquals("asc", result.getOrderByConditions().get(1).getDirection());
    }

    @Test
    void testParseOrderByWithType() {
        Map<String, Object> params = new HashMap<>();
        params.put("OrderBy", "price_Int_desc");
        QueryInfo result = QueryParser.parse(params, "article");
        assertNotNull(result);
        assertEquals(1, result.getOrderByConditions().size());
        assertEquals("t.price_", result.getOrderByConditions().get(0).getColumn());
        assertEquals("desc", result.getOrderByConditions().get(0).getDirection());
        assertEquals("Int", result.getOrderByConditions().get(0).getType());
    }

    @Test
    void testParseDefaultOrderBy() {
        Map<String, Object> params = new HashMap<>();
        QueryInfo result = QueryParser.parse(params, "article", "createdDate_desc");
        assertNotNull(result);
        assertEquals(1, result.getOrderByConditions().size());
        assertEquals("t.created_date_", result.getOrderByConditions().get(0).getColumn());
        assertEquals("desc", result.getOrderByConditions().get(0).getDirection());
    }

    @Test
    void testParseSimpleWhereCondition() {
        Map<String, Object> params = new HashMap<>();
        params.put("EQ_id_Int", "123");
        QueryInfo result = QueryParser.parse(params, "article");
        assertNotNull(result);
        assertEquals(1, result.getWhereConditions().size());
        QueryInfo.WhereCondition condition = result.getWhereConditions().get(0);
        assertEquals("t.id_", condition.getColumn());
        assertEquals("=", condition.getOperator());
        assertEquals(123, condition.getValue());
    }

    @Test
    void testParseLikeCondition() {
        Map<String, Object> params = new HashMap<>();
        params.put("Like_title", "test");
        QueryInfo result = QueryParser.parse(params, "article");
        assertNotNull(result);
        assertEquals(1, result.getWhereConditions().size());
        QueryInfo.WhereCondition condition = result.getWhereConditions().get(0);
        assertEquals("t.title_", condition.getColumn());
        assertEquals("LIKE", condition.getOperator());
        assertEquals("test", condition.getValue());
    }

    @Test
    void testParseContainsCondition() {
        Map<String, Object> params = new HashMap<>();
        params.put("Contains_name", "test");
        QueryInfo result = QueryParser.parse(params, "article");
        assertNotNull(result);
        assertEquals(1, result.getWhereConditions().size());
        QueryInfo.WhereCondition condition = result.getWhereConditions().get(0);
        assertEquals("LIKE", condition.getOperator());
        assertEquals("%test%", condition.getValue());
    }

    @Test
    void testParseStartsWithCondition() {
        Map<String, Object> params = new HashMap<>();
        params.put("StartsWith_name", "test");
        QueryInfo result = QueryParser.parse(params, "article");
        assertNotNull(result);
        QueryInfo.WhereCondition condition = result.getWhereConditions().get(0);
        assertEquals("test%", condition.getValue());
    }

    @Test
    void testParseEndsWithCondition() {
        Map<String, Object> params = new HashMap<>();
        params.put("EndsWith_name", "test");
        QueryInfo result = QueryParser.parse(params, "article");
        assertNotNull(result);
        QueryInfo.WhereCondition condition = result.getWhereConditions().get(0);
        assertEquals("%test", condition.getValue());
    }

    @Test
    void testParseInCondition() {
        Map<String, Object> params = new HashMap<>();
        params.put("In_status_Int", "1,2,3");
        QueryInfo result = QueryParser.parse(params, "article");
        assertNotNull(result);
        QueryInfo.WhereCondition condition = result.getWhereConditions().get(0);
        assertEquals("IN", condition.getOperator());
        assertTrue(condition.getValue() instanceof List);
        @SuppressWarnings("unchecked")
        List<Integer> values = (List<Integer>) condition.getValue();
        assertEquals(3, values.size());
        assertEquals(1, values.get(0));
        assertEquals(2, values.get(1));
        assertEquals(3, values.get(2));
    }

    @Test
    void testParseIsNullCondition() {
        Map<String, Object> params = new HashMap<>();
        params.put("IsNull_deletedDate", null);
        QueryInfo result = QueryParser.parse(params, "article");
        assertNotNull(result);
        QueryInfo.WhereCondition condition = result.getWhereConditions().get(0);
        assertEquals("IS NULL", condition.getOperator());
        assertNull(condition.getValue());
    }

    @Test
    void testParseIsNotNullCondition() {
        Map<String, Object> params = new HashMap<>();
        params.put("IsNotNull_deletedDate", null);
        QueryInfo result = QueryParser.parse(params, "article");
        assertNotNull(result);
        QueryInfo.WhereCondition condition = result.getWhereConditions().get(0);
        assertEquals("IS NOT NULL", condition.getOperator());
        assertNull(condition.getValue());
    }

    @Test
    void testParseComparisonOperators() {
        Map<String, Object> params = new HashMap<>();
        params.put("GT_price_Double", "100.5");
        params.put("GE_price_Double", "100.5");
        params.put("LT_price_Double", "200.5");
        params.put("LE_price_Double", "200.5");
        params.put("NE_price_Double", "150.5");
        QueryInfo result = QueryParser.parse(params, "article");
        assertNotNull(result);
        assertEquals(5, result.getWhereConditions().size());
        // Check operators individually as order may vary
        boolean foundGT = false, foundGE = false, foundLT = false, foundLE = false, foundNE = false;
        for (QueryInfo.WhereCondition condition : result.getWhereConditions()) {
            if (">".equals(condition.getOperator())) foundGT = true;
            if (">=".equals(condition.getOperator())) foundGE = true;
            if ("<".equals(condition.getOperator())) foundLT = true;
            if ("<=".equals(condition.getOperator())) foundLE = true;
            if ("<>".equals(condition.getOperator())) foundNE = true;
        }
        assertTrue(foundGT && foundGE && foundLT && foundLE && foundNE);
    }

    @Test
    void testParseWithJoinTable() {
        Map<String, Object> params = new HashMap<>();
        params.put("Like_user-username", "test");
        QueryInfo result = QueryParser.parse(params, "article");
        assertNotNull(result);
        assertEquals(1, result.getJoinTables().size());
        QueryInfo.JoinTable joinTable = result.getJoinTables().get(0);
        assertEquals("ujcms_user", joinTable.getTableName());
        assertEquals("user_", joinTable.getTableAlias());
        assertEquals("t.user_id_", joinTable.getLeftId());
        assertEquals("user_.id_", joinTable.getRightId());
    }

    @Test
    void testParseWithMultipleJoinTables() {
        Map<String, Object> params = new HashMap<>();
        params.put("Like_user-userExt-username", "test");
        QueryInfo result = QueryParser.parse(params, "article");
        assertNotNull(result);
        assertEquals(2, result.getJoinTables().size());
    }

    @Test
    void testParseWithOneToOneJoin() {
        Map<String, Object> params = new HashMap<>();
        params.put("Like_@userExt-username", "test");
        QueryInfo result = QueryParser.parse(params, "article");
        assertNotNull(result);
        assertEquals(1, result.getJoinTables().size());
        QueryInfo.JoinTable joinTable = result.getJoinTables().get(0);
        assertEquals("ujcms_user_ext", joinTable.getTableName());
        assertEquals("userExt_", joinTable.getTableAlias());
    }

    @Test
    void testParseWithOneToManyJoin() {
        Map<String, Object> params = new HashMap<>();
        params.put("Like_UserRole-roleName", "admin");
        QueryInfo result = QueryParser.parse(params, "article");
        assertNotNull(result);
        assertEquals(1, result.getJoinTables().size());
        QueryInfo.JoinTable joinTable = result.getJoinTables().get(0);
        assertEquals("ujcms_user_role", joinTable.getTableName());
    }

    @Test
    void testParseWithGroupedConditions() {
        Map<String, Object> params = new HashMap<>();
        params.put("EQ_1_status_Int", "1");
        params.put("EQ_1_type_Int", "2");
        QueryInfo result = QueryParser.parse(params, "article");
        assertNotNull(result);
        assertTrue(result.getWhereOrAndConditions().containsKey("1"));
        Map<String, List<QueryInfo.WhereCondition>> group = result.getWhereOrAndConditions().get("1");
        assertNotNull(group);
        assertTrue(group.size() >= 1);
    }

    @Test
    void testParseWithSubGroupedConditions() {
        Map<String, Object> params = new HashMap<>();
        params.put("EQ_1-1_status_Int", "1");
        params.put("EQ_1-2_type_Int", "2");
        QueryInfo result = QueryParser.parse(params, "article");
        assertNotNull(result);
        assertTrue(result.getWhereOrAndConditions().containsKey("1"));
        Map<String, List<QueryInfo.WhereCondition>> group = result.getWhereOrAndConditions().get("1");
        assertNotNull(group);
        assertTrue(group.containsKey("1"));
        assertTrue(group.containsKey("2"));
    }

    @Test
    void testParseWithJsonColumn() {
        Map<String, Object> params = new HashMap<>();
        params.put("EQ_ext$customField", "value");
        QueryInfo result = QueryParser.parse(params, "article");
        assertNotNull(result);
        assertEquals(1, result.getWhereConditions().size());
        QueryInfo.WhereCondition condition = result.getWhereConditions().get(0);
        assertNotNull(condition.getJsonColumn());
        assertEquals("customField", condition.getJsonColumn());
    }

    @Test
    void testParseWithCustomTablePrefix() {
        Map<String, Object> params = new HashMap<>();
        params.put("EQ_id_Int", "123");
        QueryInfo result = QueryParser.parse(params, "article", null, "custom_");
        assertNotNull(result);
        assertEquals("custom_", result.getTablePrefix());
        assertEquals("custom_article", result.getTableName());
    }

    @Test
    void testParseIgnoresNullOrBlankValues() {
        Map<String, Object> params = new HashMap<>();
        params.put("EQ_name", null);
        params.put("EQ_title", "");
        params.put("EQ_status_Int", "1");
        QueryInfo result = QueryParser.parse(params, "article");
        assertNotNull(result);
        // Only status condition should be added
        assertEquals(1, result.getWhereConditions().size());
        assertEquals("t.status_", result.getWhereConditions().get(0).getColumn());
    }

    @Test
    void testParseInvalidKeyFormat() {
        Map<String, Object> params = new HashMap<>();
        params.put("InvalidKey", "value");
        assertThrows(IllegalArgumentException.class, () -> {
            QueryParser.parse(params, "article");
        });
    }

    @Test
    void testParseInvalidQueryKey() {
        Map<String, Object> params = new HashMap<>();
        params.put("EQ@invalid", "value");
        // validateQuery throws QueryException, but parseQueryKey throws IllegalArgumentException
        assertThrows(Exception.class, () -> {
            QueryParser.parse(params, "article");
        });
    }

    @Test
    void testParseWithDifferentTypes() {
        Map<String, Object> params = new HashMap<>();
        params.put("EQ_price_Double", "99.99");
        params.put("EQ_count_Long", "1000");
        params.put("EQ_active_Boolean", "true");
        QueryInfo result = QueryParser.parse(params, "article");
        assertNotNull(result);
        assertEquals(3, result.getWhereConditions().size());
        // Order may vary, so check each condition individually
        boolean foundPrice = false, foundCount = false, foundActive = false;
        for (QueryInfo.WhereCondition condition : result.getWhereConditions()) {
            if (condition.getColumn().contains("price")) {
                assertEquals(99.99, condition.getValue());
                foundPrice = true;
            } else if (condition.getColumn().contains("count")) {
                assertEquals(1000L, condition.getValue());
                foundCount = true;
            } else if (condition.getColumn().contains("active")) {
                assertEquals("1", condition.getValue()); // Boolean converts to "1" or "0"
                foundActive = true;
            }
        }
        assertTrue(foundPrice && foundCount && foundActive);
    }

    @Test
    void testParseOrderByWithJsonColumn() {
        Map<String, Object> params = new HashMap<>();
        params.put("OrderBy", "ext$customField_desc");
        QueryInfo result = QueryParser.parse(params, "article");
        assertNotNull(result);
        assertEquals(1, result.getOrderByConditions().size());
        QueryInfo.OrderByCondition orderBy = result.getOrderByConditions().get(0);
        assertNotNull(orderBy.getJsonColumn());
        assertEquals("customField", orderBy.getJsonColumn());
        assertEquals("desc", orderBy.getDirection());
    }
}

