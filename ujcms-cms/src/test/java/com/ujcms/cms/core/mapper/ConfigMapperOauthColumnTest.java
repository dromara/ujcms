package com.ujcms.cms.core.mapper;

import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.security.Sm4StringTypeHandler;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ConfigMapper 语句形状测试：第三方登录设置列 {@code oauth_settings_}（含应用密钥）须在读写两侧都经
 * {@link Sm4StringTypeHandler} 加解密；{@code insert}/{@code update} 显式枚举列，遗漏即保存时静默丢弃；
 * {@code updateBase} 不得写该列。仅解析真实 ConfigMapper.xml 并断言 BoundSql 与映射，不连数据库。
 */
class ConfigMapperOauthColumnTest {
    private static final String NS = "com.ujcms.cms.core.mapper.ConfigMapper.";
    private static final String COLUMN = "oauth_settings_";
    private static final String PROPERTY = "oauthSettings";

    private static Configuration configuration;

    @BeforeAll
    static void setUp() throws Exception {
        configuration = new Configuration();
        String resource = "com/ujcms/cms/core/mapper/ConfigMapper.xml";
        try (InputStream in = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(in, configuration, resource, configuration.getSqlFragments()).parse();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"insert", "update"})
    void writeStatementsEncryptTheOauthColumn(String statementId) {
        BoundSql boundSql = configuration.getMappedStatement(NS + statementId).getBoundSql(new Config());
        assertTrue(boundSql.getSql().contains(COLUMN), statementId + " 缺少 " + COLUMN + " 列");
        ParameterMapping mapping = boundSql.getParameterMappings().stream()
                .filter(m -> PROPERTY.equals(m.getProperty())).findFirst().orElse(null);
        assertTrue(mapping != null, statementId + " 未绑定 " + PROPERTY + " 属性");
        assertInstanceOf(Sm4StringTypeHandler.class, mapping.getTypeHandler(), statementId + " 须加密写入 " + COLUMN);
    }

    @Test
    void baseUpdateNeverWritesTheOauthColumn() {
        String sql = configuration.getMappedStatement(NS + "updateBase").getBoundSql(new Config()).getSql();
        assertFalse(sql.contains(COLUMN), "updateBase 不得写 " + COLUMN);
    }

    @Test
    void resultMapDecryptsTheOauthColumn() {
        ResultMap resultMap = configuration.getResultMap(NS + "ResultMapWithBLOBs");
        assertTrue(resultMap.getResultMappings().stream().anyMatch(m -> COLUMN.equals(m.getColumn())
                        && PROPERTY.equals(m.getProperty()) && m.getTypeHandler() instanceof Sm4StringTypeHandler),
                "ResultMapWithBLOBs 缺少经 Sm4StringTypeHandler 解密的 " + COLUMN + " 映射");
    }
}
