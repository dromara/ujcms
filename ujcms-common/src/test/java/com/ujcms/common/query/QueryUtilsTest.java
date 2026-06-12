package com.ujcms.common.query;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for QueryUtils
 *
 * @author PONY
 */
class QueryUtilsTest {

    @Test
    void testCamelToUnderscore() {
        assertEquals("edit_user", QueryUtils.camelToUnderscore("editUser"));
        assertEquals("user_name", QueryUtils.camelToUnderscore("userName"));
        assertEquals("id", QueryUtils.camelToUnderscore("id"));
        assertEquals("mains_json_$abc", QueryUtils.camelToUnderscore("mainsJson$abc"));
    }

    @Test
    void testUnderscoreToCamel() {
        assertEquals("editUser", QueryUtils.underscoreToCamel("edit_user"));
        assertEquals("userName", QueryUtils.underscoreToCamel("user_name"));
        assertEquals("id", QueryUtils.underscoreToCamel("id"));
        assertEquals("customField", QueryUtils.underscoreToCamel("custom_field"));
    }

    @Test
    void testGetOperator() {
        assertEquals("LIKE", QueryUtils.getOperator("Like"));
        assertEquals("LIKE", QueryUtils.getOperator("Contains"));
        assertEquals("LIKE", QueryUtils.getOperator("StartsWith"));
        assertEquals("LIKE", QueryUtils.getOperator("EndsWith"));
        assertEquals("IN", QueryUtils.getOperator("In"));
        assertEquals("IN", QueryUtils.getOperator("ArrayIn"));
        assertEquals("NOT IN", QueryUtils.getOperator("NotIn"));
        assertEquals("IS NULL", QueryUtils.getOperator("IsNull"));
        assertEquals("IS NOT NULL", QueryUtils.getOperator("IsNotNull"));
        assertEquals("=", QueryUtils.getOperator("EQ"));
        assertEquals("=", QueryUtils.getOperator("ArrayEQ"));
        assertEquals("<>", QueryUtils.getOperator("NE"));
        assertEquals(">", QueryUtils.getOperator("GT"));
        assertEquals(">=", QueryUtils.getOperator("GE"));
        assertEquals("<", QueryUtils.getOperator("LT"));
        assertEquals("<=", QueryUtils.getOperator("LE"));
    }

    @Test
    void testGetOperatorInvalid() {
        assertThrows(QueryException.class, () -> {
            QueryUtils.getOperator("InvalidOperator");
        });
    }

    @Test
    void testIsArrayQuery() {
        assertTrue(QueryUtils.isArrayQuery("ArrayIn"));
        assertTrue(QueryUtils.isArrayQuery("ArrayEQ"));
        assertFalse(QueryUtils.isArrayQuery("In"));
        assertFalse(QueryUtils.isArrayQuery("EQ"));
    }

    @Test
    void testGetValueString() {
        assertEquals("test", QueryUtils.getValue(QueryUtils.TYPE_STRING, "test", "EQ"));
        assertEquals("%test%", QueryUtils.getValue(QueryUtils.TYPE_STRING, "test", "Contains"));
        assertEquals("test%", QueryUtils.getValue(QueryUtils.TYPE_STRING, "test", "StartsWith"));
        assertEquals("%test", QueryUtils.getValue(QueryUtils.TYPE_STRING, "test", "EndsWith"));
    }

    @Test
    void testGetValueStringIn() {
        Object result = QueryUtils.getValue(QueryUtils.TYPE_STRING, "a,b,c", "In");
        assertTrue(result instanceof List);
        @SuppressWarnings("unchecked")
        List<String> list = (List<String>) result;
        assertEquals(3, list.size());
        assertEquals("a", list.get(0));
        assertEquals("b", list.get(1));
        assertEquals("c", list.get(2));
    }

    @Test
    void testGetValueInteger() {
        assertEquals(123, QueryUtils.getValue(QueryUtils.TYPE_INT, "123", "EQ"));
        assertEquals(123, QueryUtils.getValue(QueryUtils.TYPE_INTEGER, 123, "EQ"));
        assertEquals(456, QueryUtils.getValue(QueryUtils.TYPE_INT, 456, "GT"));
    }

    @Test
    void testGetValueIntegerIn() {
        Object result = QueryUtils.getValue(QueryUtils.TYPE_INT, "1,2,3", "In");
        assertTrue(result instanceof List);
        @SuppressWarnings("unchecked")
        List<Integer> list = (List<Integer>) result;
        assertEquals(3, list.size());
        assertEquals(1, list.get(0));
        assertEquals(2, list.get(1));
        assertEquals(3, list.get(2));
    }

    @Test
    void testGetValueLong() {
        assertEquals(1000L, QueryUtils.getValue(QueryUtils.TYPE_LONG, "1000", "EQ"));
        assertEquals(2000L, QueryUtils.getValue(QueryUtils.TYPE_LONG, 2000L, "EQ"));
    }

    @Test
    void testGetValueDouble() {
        assertEquals(99.99, QueryUtils.getValue(QueryUtils.TYPE_DOUBLE, "99.99", "EQ"));
        assertEquals(100.5, QueryUtils.getValue(QueryUtils.TYPE_DOUBLE, 100.5, "GT"));
    }

    @Test
    void testGetValueBoolean() {
        assertEquals("1", QueryUtils.getValue(QueryUtils.TYPE_BOOLEAN, true, "EQ"));
        assertEquals("0", QueryUtils.getValue(QueryUtils.TYPE_BOOLEAN, false, "EQ"));
        assertEquals("1", QueryUtils.getValue(QueryUtils.TYPE_BOOLEAN, "true", "EQ"));
        assertEquals("0", QueryUtils.getValue(QueryUtils.TYPE_BOOLEAN, "false", "EQ"));
    }

    @Test
    void testGetValueIsNull() {
        assertNull(QueryUtils.getValue(QueryUtils.TYPE_STRING, "anything", "IsNull"));
        assertNull(QueryUtils.getValue(QueryUtils.TYPE_INT, "123", "IsNotNull"));
    }

    @Test
    void testGetValueBigDecimal() {
        BigDecimal value = (BigDecimal) QueryUtils.getValue(QueryUtils.TYPE_BIG_DECIMAL, "123.456", "EQ");
        assertEquals(new BigDecimal("123.456"), value);
    }

    @Test
    void testGetValueBigInteger() {
        BigInteger value = (BigInteger) QueryUtils.getValue(QueryUtils.TYPE_BIG_INTEGER, "123456789", "EQ");
        assertEquals(new BigInteger("123456789"), value);
    }

    @Test
    void testGetValueDateTime() {
        OffsetDateTime dateTime = OffsetDateTime.of(2023, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC);
        Object result = QueryUtils.getValue(QueryUtils.TYPE_DATE_TIME, dateTime, "EQ");
        assertTrue(result instanceof OffsetDateTime);
    }

    @Test
    void testGetValueDate() {
        OffsetDateTime dateTime = OffsetDateTime.of(2023, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC);
        Object result = QueryUtils.getValue(QueryUtils.TYPE_DATE, dateTime, "EQ");
        assertTrue(result instanceof OffsetDateTime);
    }

    @Test
    void testGetValueDateWithLeOperator() {
        OffsetDateTime dateTime = OffsetDateTime.of(2023, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime result = (OffsetDateTime) QueryUtils.getValue(QueryUtils.TYPE_DATE, dateTime, "LE");
        assertEquals(dateTime.plusDays(1), result);
    }

    @Test
    void testGetValueInvalidType() {
        assertThrows(QueryException.class, () -> {
            QueryUtils.getValue("InvalidType", "value", "EQ");
        });
    }

    @Test
    void testParseBoolean() {
        assertTrue(QueryUtils.parseBoolean(true));
        assertFalse(QueryUtils.parseBoolean(false));
        assertTrue(QueryUtils.parseBoolean("true"));
        assertFalse(QueryUtils.parseBoolean("false"));
        assertNull(QueryUtils.parseBoolean(null));
    }

    @Test
    void testParseBooleanInvalid() {
        assertThrows(QueryException.class, () -> {
            QueryUtils.parseBoolean(123);
        });
    }

    @Test
    void testParseInteger() {
        assertEquals(123, QueryUtils.parseInteger("123"));
        assertEquals(456, QueryUtils.parseInteger(456));
        assertNull(QueryUtils.parseInteger(null));
        assertNull(QueryUtils.parseInteger(""));
    }

    @Test
    void testParseLong() {
        assertEquals(1000L, QueryUtils.parseLong("1000"));
        assertEquals(2000L, QueryUtils.parseLong(2000L));
        assertNull(QueryUtils.parseLong(null));
    }

    @Test
    void testParseDouble() {
        assertEquals(99.99, QueryUtils.parseDouble("99.99"));
        assertEquals(100.5, QueryUtils.parseDouble(100.5));
        assertNull(QueryUtils.parseDouble(null));
    }

    @Test
    void testParseShort() {
        assertEquals((short) 123, QueryUtils.parseShort("123"));
        assertEquals((short) 456, QueryUtils.parseShort((short) 456));
        assertNull(QueryUtils.parseShort(null));
    }

    @Test
    void testParseBigDecimal() {
        BigDecimal value = QueryUtils.parseBigDecimal("123.456");
        assertEquals(new BigDecimal("123.456"), value);
        assertNull(QueryUtils.parseBigDecimal(null));
    }

    @Test
    void testParseBigInteger() {
        BigInteger value = QueryUtils.parseBigInteger("123456789");
        assertEquals(new BigInteger("123456789"), value);
        assertNull(QueryUtils.parseBigInteger(null));
    }

    @Test
    void testParseStrings() {
        List<String> result = QueryUtils.parseStrings("a,b,c");
        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals("a", result.get(0));
        assertEquals("b", result.get(1));
        assertEquals("c", result.get(2));
    }

    @Test
    void testParseStringsWithList() {
        List<String> input = Arrays.asList("a", "b", "c");
        List<String> result = QueryUtils.parseStrings(input);
        assertNotNull(result);
        assertEquals(3, result.size());
    }

    @Test
    void testParseIntegers() {
        List<Integer> result = QueryUtils.parseIntegers("1,2,3");
        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals(1, result.get(0));
        assertEquals(2, result.get(1));
        assertEquals(3, result.get(2));
    }

    @Test
    void testParseLongs() {
        List<Long> result = QueryUtils.parseLongs("1000,2000,3000");
        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals(1000L, result.get(0));
        assertEquals(2000L, result.get(1));
        assertEquals(3000L, result.get(2));
    }

    @Test
    void testParseDoubles() {
        List<Double> result = QueryUtils.parseDoubles("1.1,2.2,3.3");
        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals(1.1, result.get(0));
        assertEquals(2.2, result.get(1));
        assertEquals(3.3, result.get(2));
    }

    @Test
    void testParseShorts() {
        List<Short> result = QueryUtils.parseShorts("1,2,3");
        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals((short) 1, result.get(0));
        assertEquals((short) 2, result.get(1));
        assertEquals((short) 3, result.get(2));
    }

    @Test
    void testParseBigDecimals() {
        List<BigDecimal> result = QueryUtils.parseBigDecimals("1.1,2.2,3.3");
        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals(new BigDecimal("1.1"), result.get(0));
        assertEquals(new BigDecimal("2.2"), result.get(1));
        assertEquals(new BigDecimal("3.3"), result.get(2));
    }

    @Test
    void testParseBigIntegers() {
        List<BigInteger> result = QueryUtils.parseBigIntegers("100,200,300");
        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals(new BigInteger("100"), result.get(0));
        assertEquals(new BigInteger("200"), result.get(1));
        assertEquals(new BigInteger("300"), result.get(2));
    }

    @Test
    void testParseString() {
        assertEquals("test", QueryUtils.parseString("test"));
        assertEquals("123", QueryUtils.parseString(123));
        assertNull(QueryUtils.parseString(null));
    }

    @Test
    void testParseDate() {
        OffsetDateTime dateTime = OffsetDateTime.of(2023, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime result = QueryUtils.parseDate(dateTime);
        assertEquals(dateTime, result);
    }

    @Test
    void testParseDateFromString() {
        OffsetDateTime result = QueryUtils.parseDate("2023-01-01T12:00:00Z");
        assertNotNull(result);
    }

    @Test
    void testParseDateFromDate() {
        Date date = new Date();
        OffsetDateTime result = QueryUtils.parseDate(date);
        assertNotNull(result);
    }

    @Test
    void testParseDateNull() {
        assertNull(QueryUtils.parseDate(null));
        assertNull(QueryUtils.parseDate(""));
    }

    @Test
    void testJoinByComma() {
        assertEquals("a,b,c", QueryUtils.joinByComma(Arrays.asList("a", "b", "c")));
        assertEquals("a", QueryUtils.joinByComma(Collections.singletonList("a")));
        assertEquals("", QueryUtils.joinByComma(Collections.emptyList()));
    }

    @Test
    void testJoinByCommaIgnoresNullAndEmpty() {
        assertEquals("a,c", QueryUtils.joinByComma(Arrays.asList("a", null, "", "c")));
    }

    @Test
    void testValidateQuery() {
        assertDoesNotThrow(() -> QueryUtils.validateQuery("EQ_name"));
        assertDoesNotThrow(() -> QueryUtils.validateQuery("Like_user-username"));
        assertDoesNotThrow(() -> QueryUtils.validateQuery("EQ_ext$field"));
    }

    @Test
    void testValidateQueryInvalid() {
        assertThrows(QueryException.class, () -> {
            QueryUtils.validateQuery("EQ name"); // contains space
        });
        assertThrows(QueryException.class, () -> {
            QueryUtils.validateQuery("EQ#name"); // contains #
        });
    }

    @Test
    void testValidateTable() {
        assertDoesNotThrow(() -> QueryUtils.validateTable("article"));
        assertDoesNotThrow(() -> QueryUtils.validateTable("user_ext"));
    }

    @Test
    void testValidateTableInvalid() {
        assertThrows(QueryException.class, () -> {
            QueryUtils.validateTable("article-table"); // contains dash
        });
    }

    @Test
    void testValidateField() {
        assertDoesNotThrow(() -> QueryUtils.validateField("username"));
        assertDoesNotThrow(() -> QueryUtils.validateField("user.name"));
    }

    @Test
    void testValidateFieldInvalid() {
        assertThrows(QueryException.class, () -> {
            QueryUtils.validateField("user-name"); // contains dash
        });
    }

    @Test
    void testValidateJsonField() {
        assertDoesNotThrow(() -> QueryUtils.validateJsonField("customField"));
    }

    @Test
    void testValidateJsonFieldInvalid() {
        assertThrows(QueryException.class, () -> {
            QueryUtils.validateJsonField("custom-field"); // contains dash
        });
    }

    @Test
    void testValidateOrderBy() {
        assertDoesNotThrow(() -> QueryUtils.validateOrderBy("createdDate_desc"));
        assertDoesNotThrow(() -> QueryUtils.validateOrderBy("createdDate_desc,id_asc"));
        assertDoesNotThrow(() -> QueryUtils.validateOrderBy(null));
        assertDoesNotThrow(() -> QueryUtils.validateOrderBy(""));
    }

    @Test
    void testValidateOrderByInvalid() {
        assertThrows(QueryException.class, () -> {
            QueryUtils.validateOrderBy("createdDate#desc"); // contains #
        });
    }

    @Test
    void testGetQueryMap() {
        String queryString = "Q_EQ_id=123&Q_Like_name=test&other=value";
        Map<String, Object> result = QueryUtils.getQueryMap(queryString);
        assertNotNull(result);
        assertEquals("123", result.get("EQ_id"));
        assertEquals("test", result.get("Like_name"));
        assertFalse(result.containsKey("other"));
    }

    @Test
    void testGetQueryMapWithPrefix() {
        Map<String, String> params = Map.of(
            "prefix_EQ_id", "123",
            "prefix_Like_name", "test",
            "other", "value"
        );
        Map<String, Object> result = QueryUtils.getQueryMap(params, "prefix_");
        assertNotNull(result);
        assertEquals("123", result.get("EQ_id"));
        assertEquals("test", result.get("Like_name"));
        assertFalse(result.containsKey("other"));
    }
}

