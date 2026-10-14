package com.ujcms.cms.core;

import com.ujcms.cms.core.service.InstallDemoPort;
import com.ujcms.cms.core.service.InstallService;
import com.ujcms.cms.core.service.ThemePackageService;
import com.ujcms.cms.core.support.InstallAutoRunner;
import com.ujcms.cms.core.support.Props;
import com.ujcms.common.db.CharBooleanTypeHandler;
import com.ujcms.common.db.JsonStringTypeHandler;
import com.ujcms.common.db.identifier.SnowflakeSequence;
import org.apache.ibatis.mapping.VendorDatabaseIdProvider;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;


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
        if (datacenterId < 0 && workerId < 0) {
            return new SnowflakeSequence();
        }
        // 配置了就必须配置完整且合法，启动即报错，避免集群中静默退回自动推算而产生重复 ID
        if (datacenterId < 0 || workerId < 0) {
            throw new IllegalStateException("ujcms.datacenterId and ujcms.workerId must be set together");
        }
        return new SnowflakeSequence(datacenterId, workerId);
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
     * 无人值守自动安装引擎（install-flow §7 完整清单）。仅 {@code ujcms.install.auto=true}
     * 时注册；末位 ApplicationRunner，晚于构成注册表首个快照。
     */
    @Bean
    @ConditionalOnProperty(prefix = "ujcms.install", name = "auto", havingValue = "true")
    public InstallAutoRunner installAutoRunner(InstallService installService,
                                               ThemePackageService themePackageService,
                                               ObjectProvider<InstallDemoPort> demoPort, Props props) {
        return new InstallAutoRunner(installService, themePackageService, demoPort, props);
    }
}
