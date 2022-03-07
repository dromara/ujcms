package com.ofwise.util.db;

import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.DatabaseException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.lang.Nullable;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * 使用数据脚本初始化数据
 *
 * @author PONY
 * @see org.springframework.boot.autoconfigure.sql.init.SqlDataSourceScriptDatabaseInitializer
 * @see org.springframework.boot.sql.init.AbstractScriptDatabaseInitializer
 */
public class DataScriptInitializer implements InitializingBean {
    protected final Logger logger = LoggerFactory.getLogger(DataScriptInitializer.class);

    private final DataSource dataSource;
    private volatile ResourceLoader resourceLoader;
    private String checkTable;
    @Nullable
    private String platform;

    public DataScriptInitializer(DataSource dataSource, ResourceLoader resourceLoader, String checkTable,
                                 @Nullable String platform) {
        this.dataSource = dataSource;
        this.resourceLoader = resourceLoader;
        this.checkTable = checkTable;
        this.platform = platform;
    }

    public String getDatabasePlatform(DataSource dataSource) throws DatabaseException {
        Connection connection = null;
        Database database = null;
        String name;
        try {
            connection = dataSource.getConnection();
            database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            name = database.getShortName();
        } catch (SQLException e) {
            throw new DatabaseException(e);
        } finally {
            if (database != null) {
                database.close();
            } else {
                if (connection != null) {
                    try {
                        if (!connection.getAutoCommit()) {
                            connection.rollback();
                        }
                        connection.close();
                    } catch (SQLException e) {
                        logger.warn("problem closing connection", e);
                    }
                }
            }
        }
        return name;
    }

    private String getDataSqlLocation() {
        if (StringUtils.isBlank(platform)) {
            try {
                platform = getDatabasePlatform(dataSource);
            } catch (DatabaseException e) {
                throw new RuntimeException(e);
            }
        }
        return "classpath:/db/data-" + platform + ".sql";
    }

    private String getCheckTableSql() {
        return "select count(*) from " + checkTable;
    }

    @Override
    public void afterPropertiesSet() {
        initializeDatabase();
    }

    public void initializeDatabase() {
        Integer hasData = new JdbcTemplate(dataSource).queryForObject(getCheckTableSql(), Integer.TYPE);
        if (hasData != null && hasData > 0) {
            return;
        }
        String location = getDataSqlLocation();
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            return;
        }
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.setSqlScriptEncoding(StandardCharsets.UTF_8.displayName());
        populator.addScript(resource);
        DatabasePopulatorUtils.execute(populator, this.dataSource);
    }
}
