package com.ujcms.commons.db;

import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.generator.LuceneGenerator;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.service.SiteService;
import com.ujcms.cms.core.service.UserService;
import com.ujcms.cms.core.support.Props;
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
    private final ResourceLoader resourceLoader;
    private final LuceneGenerator luceneGenerator;
    private final ConfigService configService;
    private final SiteService siteService;
    private final UserService userService;
    private final Props props;
    private final String checkTable;

    public DataScriptInitializer(DataSource dataSource, ResourceLoader resourceLoader, LuceneGenerator luceneGenerator,
                                 ConfigService configService, SiteService siteService, UserService userService,
                                 Props props, String checkTable) {
        this.dataSource = dataSource;
        this.resourceLoader = resourceLoader;
        this.luceneGenerator = luceneGenerator;
        this.configService = configService;
        this.siteService = siteService;
        this.userService = userService;
        this.props = props;
        this.checkTable = checkTable;
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
        String platform = props.getDataSqlPlatform();
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
        initialize();
    }

    public void initialize() {
        Integer hasData = new JdbcTemplate(dataSource).queryForObject(getCheckTableSql(), Integer.TYPE);
        if (hasData != null && hasData > 0) {
            return;
        }
        String location = getDataSqlLocation();
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            return;
        }
        // 初始化数据库脚本
        initScript(resource);
        // 初始化域名
        initDomain();
        // 初始化用户
        initUser();
        // 初始化 Lucene
        initLucene();
    }

    private void initScript(Resource resource) {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.setSqlScriptEncoding(StandardCharsets.UTF_8.name());
        populator.addScript(resource);
        DatabasePopulatorUtils.execute(populator, this.dataSource);
    }

    private void initDomain() {
        String initDomain = props.getInitDomain();
        if (StringUtils.isNotBlank(initDomain)) {
            String initProtocol = props.getInitProtocol();
            String initPort = props.getInitPort();
            siteService.updateDomain(1L, initProtocol, initDomain);
            if (StringUtils.isNotBlank(initPort)) {
                configService.updatePort(Integer.parseInt(initPort));
            } else {
                configService.updatePort(null);
            }
        }
    }

    private void initUser() {
        User user = userService.select(1L);
        if (user == null) {
            return;
        }
        String username = props.getInitUsername();
        if (StringUtils.isNotBlank(username)) {
            user.setUsername(username);
            userService.update(user);
        }
        String password = props.getInitPassword();
        if (StringUtils.isNotBlank(password)) {
            userService.updatePassword(user, user.getExt(), password);
        }
    }

    private void initLucene() {
        luceneGenerator.reindex(1L, 1L, "init database");
    }
}
