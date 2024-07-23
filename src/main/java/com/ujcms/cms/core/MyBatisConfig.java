package com.ujcms.cms.core;

import com.ujcms.cms.core.support.Props;
import com.ujcms.commons.db.CharBooleanTypeHandler;
import com.ujcms.commons.db.DataScriptInitializer;
import com.ujcms.commons.db.JsonStringTypeHandler;
import com.ujcms.commons.db.identifier.SnowflakeSequence;
import org.apache.ibatis.mapping.VendorDatabaseIdProvider;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.io.ResourceLoader;

import javax.sql.DataSource;
import java.util.Properties;

import static com.ujcms.commons.db.identifier.SnowflakeSequence.MAX_DATACENTER_ID;
import static com.ujcms.commons.db.identifier.SnowflakeSequence.MAX_WORKER_ID;

/**
 * MyBatis 配置
 *
 * @author PONY
 */
@Configuration
public class MyBatisConfig {
    /**
     * 雪花算法ID生成器
     */
    @Bean
    public SnowflakeSequence snowflakeSequence(Props props) {
        int datacenterId = props.getDatacenterId();
        int workerId = props.getWorkerId();
        if (datacenterId >= 0 && datacenterId <= MAX_DATACENTER_ID && workerId >= 0 && workerId <= MAX_WORKER_ID) {
            return new SnowflakeSequence(datacenterId, workerId);
        }
        return new SnowflakeSequence(null);
    }

    /**
     * 数值型 boolean 类型处理
     */
    @Bean
    public TypeHandler<Boolean> charBooleanTypeHandler() {
        return new CharBooleanTypeHandler();
    }

    /**
     * JSON 转 String 类型处理
     */
    @Bean
    public ConfigurationCustomizer mybatisConfigurationCustomizer() {
        return configuration -> configuration.getTypeHandlerRegistry()
                .register(String.class, JdbcType.OTHER, new JsonStringTypeHandler(configuration));
    }

    /**
     * 多数据库支持
     */
    @Bean
    public VendorDatabaseIdProvider databaseIdProvider() {
        VendorDatabaseIdProvider databaseIdProvider = new VendorDatabaseIdProvider();
        Properties properties = new Properties();
        properties.put("MySQL", "mysql");
        properties.put("MariaDB", "mariadb");
        properties.put("PostgreSQL", "postgresql");
        properties.put("H2", "h2");
        properties.put("Oracle", "oracle");
        properties.put("SQL Server", "sqlserver");
        properties.put("DB2", "db2");
        // 达梦
        properties.put("DM DBMS", "dm");
        // 人大金仓的名称是KingbaseES，判断规则为contains，只要包含部分字符串即可。
        // 为防止金仓改变ES名称或还有其它类似产品线，此处判断包含kingbase，而不判断KingbaseES
        properties.put("Kingbase", "kingbase");
        // 南大通用
        properties.put("GBase", "gbase");
        // 神州通用
        properties.put("OSCAR", "oscar");

        databaseIdProvider.setProperties(properties);
        return databaseIdProvider;
    }

    /**
     * 数据初始化
     */
    @Bean
    @DependsOn("liquibase")
    @ConditionalOnProperty(prefix = "ujcms", name = "data-sql-enabled", matchIfMissing = true)
    public DataScriptInitializer databaseInitializer(
            Props props, DataSource dataSource, ResourceLoader resourceLoader) {
        return new DataScriptInitializer(dataSource, resourceLoader, "ujcms_config", props.getDataSqlPlatform());
    }
}
