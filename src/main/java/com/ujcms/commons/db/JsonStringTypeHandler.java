package com.ujcms.commons.db;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import org.springframework.lang.Nullable;

import java.io.StringReader;
import java.sql.*;

/**
 * Json 和 Map 的映射
 *
 * @author PONY
 */
@MappedJdbcTypes(JdbcType.OTHER)
@MappedTypes(String.class)
public class JsonStringTypeHandler extends BaseTypeHandler<String> {
    private final Configuration mybatisConfiguration;

    public JsonStringTypeHandler(Configuration mybatisConfiguration) {
        this.mybatisConfiguration = mybatisConfiguration;
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType)
            throws SQLException {
        String databaseId = mybatisConfiguration.getDatabaseId();
        switch (databaseId) {
            case POSTGRESQL:
                ps.setObject(i, parameter, Types.OTHER);
                break;
            case ORACLE:
            case DM:
                if (StringUtils.isBlank(parameter)) {
                    ps.setNull(i, Types.LONGVARCHAR);
                } else {
                    StringReader reader = new StringReader(parameter);
                    ps.setCharacterStream(i, reader, parameter.length());
                }
                break;
            default:
                ps.setString(i, parameter);
        }
    }

    @Nullable
    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        Object result = rs.getObject(columnName);
        return convert(result);
    }

    @Nullable
    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        Object result = rs.getObject(columnIndex);
        return convert(result);
    }

    @Nullable
    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        Object result = cs.getObject(columnIndex);
        return convert(result);
    }

    @Nullable
    private String convert(@Nullable Object result) throws SQLException {
        if (result == null) {
            return null;
        }
        if (result instanceof Clob) {
            // Oracle、DM(达梦) 返回 Clob 类型
            Clob clob = (Clob) result;
            return clob.getSubString(1, (int) clob.length());
        } else if (result instanceof String) {
            // MySQL 返回 String 类型
            return (String) result;
        } else {
            // Postgresql 为 org.postgresql.util.PGobject。调用 toString() 方法即可
            return result.toString();
        }
    }

    private static final String POSTGRESQL = "postgresql";
    private static final String DM = "dm";
    private static final String ORACLE = "oracle";
}
