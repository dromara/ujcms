package com.ujcms.cms.core.mapper;

import com.ujcms.cms.core.domain.Site;
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
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 多语言寻址（multilingual P1）SiteMapper 语句形状测试，守卫 plan.md R1：
 * {@code insert}/{@code update}/{@code updateBase} 均显式枚举列，遗漏 lang_/lang_parent_id_
 * 会在保存时静默丢弃字段。
 *
 * <p>仅解析真实 SiteMapper.xml 并断言 BoundSql 文本与参数映射（不连数据库——原 H2
 * round-trip 版本随 H2 支持一并移除，变更集的方言级验证转由目标库实测承担）。
 */
class SiteMapperLangColumnTest {
    private static final String NS = "com.ujcms.cms.core.mapper.SiteMapper.";
    /** lang_ 精确匹配：后接非单词字符，避免命中 lang_parent_id_ */
    private static final Pattern LANG_COLUMN = Pattern.compile("\\blang_\\b");

    private static Configuration configuration;

    @BeforeAll
    static void setUp() throws Exception {
        configuration = new Configuration();
        // SeqMapper.xml 提供 selectAll 依赖的 Select_All 片段，须先解析
        for (String resource : new String[]{"com/ujcms/cms/core/mapper/SeqMapper.xml",
                "com/ujcms/cms/core/mapper/SiteMapper.xml"}) {
            try (InputStream in = Resources.getResourceAsStream(resource)) {
                new XMLMapperBuilder(in, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"insert", "update", "updateBase"})
    void writeStatementsEnumerateLangColumns(String statementId) {
        BoundSql boundSql = configuration.getMappedStatement(NS + statementId).getBoundSql(new Site());
        String sql = boundSql.getSql();
        assertTrue(LANG_COLUMN.matcher(sql).find(), statementId + " 缺少 lang_ 列");
        assertTrue(sql.contains("lang_parent_id_"), statementId + " 缺少 lang_parent_id_ 列");

        List<String> properties = boundSql.getParameterMappings().stream()
                .map(ParameterMapping::getProperty).toList();
        assertTrue(properties.contains("lang"), statementId + " 未绑定 lang 属性");
        assertTrue(properties.contains("langParentId"), statementId + " 未绑定 langParentId 属性");
    }

    @Test
    void columnListAndBaseResultMapCarryLangColumns() {
        String selectSql = configuration.getMappedStatement(NS + "select").getBoundSql(101L).getSql();
        assertTrue(selectSql.contains("t.lang_"), "Column_List 缺少 t.lang_");
        assertTrue(selectSql.contains("t.lang_parent_id_"), "Column_List 缺少 t.lang_parent_id_");

        ResultMap baseResultMap = configuration.getResultMap(NS + "BaseResultMap");
        assertTrue(baseResultMap.getResultMappings().stream()
                        .anyMatch(m -> "lang_".equals(m.getColumn()) && "lang".equals(m.getProperty())),
                "BaseResultMap 缺少 lang_ 映射");
        assertTrue(baseResultMap.getResultMappings().stream()
                        .anyMatch(m -> "lang_parent_id_".equals(m.getColumn()) && "langParentId".equals(m.getProperty())),
                "BaseResultMap 缺少 lang_parent_id_ 映射");
    }
}
