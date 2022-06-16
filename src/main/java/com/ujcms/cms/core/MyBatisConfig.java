package com.ujcms.cms.core;

import com.ujcms.util.db.DataScriptInitializer;
import com.ujcms.util.db.NumericBooleanTypeHandler;
import com.ujcms.cms.core.support.Props;
import org.apache.ibatis.mapping.VendorDatabaseIdProvider;
import org.apache.ibatis.type.TypeHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.io.ResourceLoader;

import javax.sql.DataSource;
import java.util.Properties;

/**
 * MyBatis 配置
 *
 * @author PONY
 */
@Configuration
public class MyBatisConfig {
    /**
     * 数值型 boolean 类型处理
     */
    @Bean
    public TypeHandler<Boolean> numericBooleanTypeHandler() {
        return new NumericBooleanTypeHandler();
    }

    /**
     * 多数据库支持
     */
    @Bean
    public VendorDatabaseIdProvider databaseIdProvider() {
        VendorDatabaseIdProvider databaseIdProvider = new VendorDatabaseIdProvider();
        Properties properties = new Properties();
        properties.put("MySQL", "mysql");
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
    public DataScriptInitializer databaseInitializer(Props props,
                                                     DataSource dataSource, ResourceLoader resourceLoader) {
        return new DataScriptInitializer(dataSource, resourceLoader, "ujcms_config", props.getDataSqlPlatform());
    }
}
