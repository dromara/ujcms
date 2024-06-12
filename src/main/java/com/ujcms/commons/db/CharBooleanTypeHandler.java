package com.ujcms.commons.db;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import org.springframework.lang.Nullable;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 字符型 Boolean 类型处理器。'1' 代表 true，'0' 代表 false。
 * <p>
 * 布尔类型在不同数据库有不同的实现方式，很多数据库甚至没有布尔类型。
 * 数据库中尽量使用通用的数据类型，在跨数据库平台的情况下，不通用的数据类型会导致很多麻烦。
 * 使用数值型的 0 1 代表 boolean 的 false true，是不错的选择。
 * 但有些数据库（如`PostgreSQL`）没有`tinyint`，而`smallint`又要用于枚举类型。
 * 因此`char(1)`变成了更好的选择。
 *
 * @author PONY
 */
@MappedJdbcTypes(JdbcType.CHAR)
@MappedTypes(Boolean.class)
public class CharBooleanTypeHandler extends BaseTypeHandler<Boolean> {
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Boolean parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, convert(parameter));
    }

    @Nullable
    @Override
    public Boolean getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String result = rs.getString(columnName);
        return result == null ? null : convert(result);
    }

    @Nullable
    @Override
    public Boolean getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String result = rs.getString(columnIndex);
        return result == null ? null : convert(result);
    }

    @Nullable
    @Override
    public Boolean getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String result = cs.getString(columnIndex);
        return result == null ? null : convert(result);
    }

    private String convert(Boolean b) {
        return Boolean.TRUE.equals(b) ? "1" : "0";
    }

    private Boolean convert(String s) {
        return "1".equalsIgnoreCase(s);
    }
}
