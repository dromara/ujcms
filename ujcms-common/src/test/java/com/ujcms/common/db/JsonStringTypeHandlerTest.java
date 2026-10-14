package com.ujcms.common.db;

import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.sql.Clob;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * JSON 列的读取按驱动返回的 Java 类型分路：MySQL Connector/J 与 MariaDB 服务器给 String；MariaDB Connector/J 连接 MySQL 服务器时
 * JSON 列按 binary 字符集给 byte[]（UTF-8 文本）；Oracle / 达梦给 Clob；PostgreSQL 给 PGobject（toString）
 */
class JsonStringTypeHandlerTest {
    private final JsonStringTypeHandler handler = new JsonStringTypeHandler(new Configuration());

    @Test
    void stringComesBackAsIs() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getObject("customs_json_")).thenReturn("{\"a\":1}");
        assertEquals("{\"a\":1}", handler.getNullableResult(rs, "customs_json_"));
    }

    @Test
    void mysqlJsonThroughTheMariadbDriverArrivesAsUtf8Bytes() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getObject(1)).thenReturn("{\"标题\":\"中文\"}".getBytes(StandardCharsets.UTF_8));
        assertEquals("{\"标题\":\"中文\"}", handler.getNullableResult(rs, 1));
    }

    @Test
    void clobNullAndOtherObjects() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        Clob clob = mock(Clob.class);
        when(clob.length()).thenReturn(7L);
        when(clob.getSubString(1, 7)).thenReturn("{\"b\":2}");
        when(rs.getObject("c")).thenReturn(clob);
        when(rs.getObject("n")).thenReturn(null);
        when(rs.getObject("o")).thenReturn(new StringBuilder("{\"c\":3}"));
        assertEquals("{\"b\":2}", handler.getNullableResult(rs, "c"));
        assertNull(handler.getNullableResult(rs, "n"));
        assertEquals("{\"c\":3}", handler.getNullableResult(rs, "o"));
    }
}
