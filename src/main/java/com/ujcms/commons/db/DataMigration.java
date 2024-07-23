package com.ujcms.commons.db;

import org.apache.commons.lang3.ArrayUtils;
import org.springframework.lang.Nullable;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 数据迁移工具类
 *
 * @author PONY
 */
public class DataMigration {
    public static List<String> getColumns(ResultSet rs) throws SQLException {
        ResultSetMetaData metaData = rs.getMetaData();
        int columnsCount = metaData.getColumnCount();
        List<String> columns = new ArrayList<>(columnsCount);
        for (int i = 1; i <= columnsCount; i += 1) {
            columns.add(metaData.getColumnLabel(i));
        }
        return columns;
    }

    @Nullable
    public static String getRsString(ResultSet rs, List<String> columns, String column) throws SQLException {
        if (!columns.contains(column)) {
            return null;
        }
        final Object obj = rs.getObject(column);
        if (obj == null) {
            return null;
        } else if (obj instanceof String) {
            return (String) obj;
        } else {
            return obj.toString();
        }
    }

    public static String getRsStringRequired(ResultSet rs, List<String> columns, String column) throws SQLException {
        final String result = getRsString(rs, columns, column);
        assertNotNull(result, column);
        return result;
    }

    @Nullable
    public static Long getRsLong(ResultSet rs, List<String> columns, String column) throws SQLException {
        if (!columns.contains(column)) {
            return null;
        }
        final Object obj = rs.getObject(column);
        if (obj instanceof Long) {
            return (Long) obj;
        } else if (obj instanceof Number) {
            return ((Number) obj).longValue();
        } else if (obj instanceof String) {
            return Long.parseLong((String) obj);
        } else {
            return null;
        }
    }

    public static Long getRsLongRequired(ResultSet rs, List<String> columns, String column) throws SQLException {
        final Long result = getRsLong(rs, columns, column);
        assertNotNull(result, column);
        return result;
    }

    @Nullable
    public static Integer getRsInteger(ResultSet rs, List<String> columns, String column) throws SQLException {
        if (!columns.contains(column)) {
            return null;
        }
        final Object obj = rs.getObject(column);
        if (obj instanceof Integer) {
            return (Integer) obj;
        } else if (obj instanceof Number) {
            return ((Number) obj).intValue();
        } else if (obj instanceof String) {
            return Integer.parseInt((String) obj);
        } else {
            return null;
        }
    }

    public static Integer getRsIntegerRequired(ResultSet rs, List<String> columns, String column) throws SQLException {
        final Integer result = getRsInteger(rs, columns, column);
        assertNotNull(result, column);
        return result;
    }

    @Nullable
    public static Short getRsShort(ResultSet rs, List<String> columns, String column) throws SQLException {
        if (!columns.contains(column)) {
            return null;
        }
        final Object obj = rs.getObject(column);
        if (obj instanceof Short) {
            return (Short) obj;
        } else if (obj instanceof Number) {
            return ((Number) obj).shortValue();
        } else if (obj instanceof String) {
            return Short.parseShort((String) obj);
        } else {
            return null;
        }
    }

    public static Short getRsShortRequired(ResultSet rs, List<String> columns, String column) throws SQLException {
        final Short result = getRsShort(rs, columns, column);
        assertNotNull(result, column);
        return result;
    }

    @Nullable
    public static Boolean getRsBoolean(ResultSet rs, List<String> columns, String column) throws SQLException {
        if (!columns.contains(column)) {
            return null;
        }
        final Object obj = rs.getObject(column);
        if (obj instanceof String) {
            String s = ((String) obj).toLowerCase();
            if (ArrayUtils.contains(TRUE_STRINGS, s)) {
                return Boolean.TRUE;
            } else if (ArrayUtils.contains(FALSE_STRINGS, s)) {
                return Boolean.FALSE;
            } else {
                throw new IllegalArgumentException(CANNOT_BE_NULL + s);
            }
        } else if (obj instanceof Number) {
            int i = ((Number) obj).intValue();
            // 非 0 为真
            return i != 0;
        } else if (obj instanceof Boolean) {
            return (Boolean) obj;
        } else {
            return null;
        }
    }

    public static Boolean getRsBooleanRequired(ResultSet rs, List<String> columns, String column) throws SQLException {
        final Boolean result = getRsBoolean(rs, columns, column);
        assertNotNull(result, column);
        return result;
    }

    @Nullable
    public static OffsetDateTime getRsOffsetDateTime(ResultSet rs, List<String> columns, String column)
            throws SQLException {
        if (!columns.contains(column)) {
            return null;
        }
        final Object obj = rs.getObject(column);
        if (obj instanceof LocalDateTime) {
            return ((LocalDateTime) obj).atZone(ZoneId.systemDefault()).toOffsetDateTime();
        } else if (obj instanceof OffsetDateTime) {
            return (OffsetDateTime) obj;
        } else if (obj instanceof ZonedDateTime) {
            return ((ZonedDateTime) obj).toOffsetDateTime();
        } else if (obj instanceof Date) {
            final Instant instant = Instant.ofEpochMilli(((Date) obj).getTime());
            return OffsetDateTime.ofInstant(instant, ZoneId.systemDefault());
        } else if (obj instanceof Number) {
            long milli = ((Number) obj).longValue();
            return OffsetDateTime.ofInstant(Instant.ofEpochMilli(milli), ZoneId.systemDefault());
        } else {
            return null;
        }
    }

    public static OffsetDateTime getRsOffsetDateTimeRequired(ResultSet rs, List<String> columns, String column)
            throws SQLException {
        final OffsetDateTime result = getRsOffsetDateTime(rs, columns, column);
        assertNotNull(result, column);
        return result;
    }

    private static void assertNotNull(@Nullable Object result, String column) {
        if (result == null) {
            throw new IllegalStateException(CANNOT_BE_NULL + column);
        }
    }

    private static final String[] TRUE_STRINGS = {"true", "1", "t"};
    private static final String[] FALSE_STRINGS = {"false", "0", "f"};

    private static final String CANNOT_BE_NULL = "ResultSet column cannot be null: ";

    private DataMigration() {
        throw new IllegalStateException("Utility class");
    }
}
