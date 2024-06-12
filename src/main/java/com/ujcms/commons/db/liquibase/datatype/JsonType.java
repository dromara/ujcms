package com.ujcms.commons.db.liquibase.datatype;

import liquibase.change.core.LoadDataChange;
import liquibase.database.Database;
import liquibase.database.core.MSSQLDatabase;
import liquibase.database.core.MySQLDatabase;
import liquibase.database.core.OracleDatabase;
import liquibase.datatype.DataTypeInfo;
import liquibase.datatype.DatabaseDataType;
import liquibase.datatype.LiquibaseDataType;
import liquibase.servicelocator.PrioritizedService;
import liquibase.statement.DatabaseFunction;
import liquibase.util.StringUtil;

import java.util.Locale;

/**
 * @author PONY
 */
@DataTypeInfo(name = "json", aliases = {"jsonb"}, minParameters = 0, maxParameters = 0, priority = PrioritizedService.PRIORITY_DEFAULT)
public class JsonType extends LiquibaseDataType {
    private static final String NULL = "null";

    @Override
    public String objectToSql(Object value, Database database) {
        if ((value == null) || NULL.equals(value.toString().toLowerCase(Locale.US))) {
            return null;
        }

        if (value instanceof DatabaseFunction) {
            return value.toString();
        }

        String val = String.valueOf(value);
        if ((database instanceof MSSQLDatabase) && !StringUtil.isAscii(val)) {
            return "N'" + database.escapeStringForDatabase(val) + "'";
        }

        return "'" + database.escapeStringForDatabase(val) + "'";
    }

    @Override
    public DatabaseDataType toDatabaseDataType(Database database) {
        if (database instanceof MySQLDatabase) {
            return new DatabaseDataType("JSON");
        }
        if (database instanceof OracleDatabase) {
            return new DatabaseDataType("CLOB");
        }
        // PostgresDatabase or others
        return new DatabaseDataType("jsonb");
    }

    @Override
    public LoadDataChange.LOAD_DATA_TYPE getLoadTypeName() {
        return LoadDataChange.LOAD_DATA_TYPE.STRING;
    }
}
