package com.ofwise.util.typehandler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 数值型 Boolean 类型处理器。1 代表 true，0 代表 false。
 * <p>
 * 布尔类型在不同数据库有不同的实现方式，很多数据库甚至没有布尔类型。
 * 数据库中尽量使用通用的数据类型，在跨数据库平台的情况下，不通用的数据类型会导致很多麻烦。
 * 使用数值型的 0 1 代表 boolean 的 false true，是最合理的选择。
 *
 * @author PONY
 */
@MappedJdbcTypes(JdbcType.BOOLEAN)
public class NumericBooleanTypeHandler extends BaseTypeHandler<Boolean> {
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Boolean parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setShort(i, convert(parameter));
    }

    @Override
    public Boolean getNullableResult(ResultSet rs, String columnName) throws SQLException {
        short result = rs.getShort(columnName);
        return result == 0 && rs.wasNull() ? null : convert(result);
    }

    @Override
    public Boolean getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        short result = rs.getShort(columnIndex);
        return result == 0 && rs.wasNull() ? null : convert(result);
    }

    @Override
    public Boolean getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        short result = cs.getShort(columnIndex);
        return result == 0 && cs.wasNull() ? null : convert(result);
    }

    private short convert(Boolean b) {
        return b ? (short) 1 : 0;
    }

    private Boolean convert(short s) {
        return s == 1;
    }
}
