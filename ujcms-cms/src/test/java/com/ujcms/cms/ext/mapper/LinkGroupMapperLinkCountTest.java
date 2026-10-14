package com.ujcms.cms.ext.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LinkGroupMapper 语句形状测试（docs/spec/console-master-detail）：链接数子查询只在要求统计时出现——
 * 指令、公开接口、种子导出等其余调用方不付这笔代价；{@code ListResultMap} 把 link_count_ 映射到 linkCount。
 *
 * <p>仅解析真实 LinkGroupMapper.xml 并断言 BoundSql 文本（不连数据库）：XML 写坏、{@code @Param} 名与
 * {@code <if test>} 对不上，都会在这里而不是在启动后暴露。
 */
class LinkGroupMapperLinkCountTest {
    private static final String NS = "com.ujcms.cms.ext.mapper.LinkGroupMapper.";

    private static Configuration configuration;

    @BeforeAll
    static void setUp() throws Exception {
        configuration = new Configuration();
        // SeqMapper.xml 提供 selectAll 依赖的 Select_All 片段，须先解析
        for (String resource : new String[]{"com/ujcms/cms/core/mapper/SeqMapper.xml",
                "com/ujcms/cms/ext/mapper/LinkGroupMapper.xml"}) {
            try (InputStream in = Resources.getResourceAsStream(resource)) {
                new XMLMapperBuilder(in, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
    }

    private static String selectAllSql(boolean isQueryLinkCount) {
        Map<String, Object> params = new HashMap<>(4);
        params.put("queryInfo", null);
        params.put("isQueryLinkCount", isQueryLinkCount);
        return configuration.getMappedStatement(NS + "selectAll").getBoundSql(params).getSql();
    }

    @Test
    void linkCountSubqueryOnlyWhenRequested() {
        String counted = selectAllSql(true);
        assertTrue(counted.contains("from ujcms_link t_link_ where t_link_.group_id_ = t.id_"), counted);
        assertTrue(counted.contains("as link_count_"), counted);

        assertFalse(selectAllSql(false).contains("link_count_"));
    }

    @Test
    void listResultMapCarriesLinkCount() {
        assertEquals(NS + "ListResultMap", configuration.getMappedStatement(NS + "selectAll").getResultMaps().get(0).getId());
        ResultMap listResultMap = configuration.getResultMap(NS + "ListResultMap");
        assertTrue(listResultMap.getResultMappings().stream()
                .anyMatch(m -> "link_count_".equals(m.getColumn()) && "linkCount".equals(m.getProperty())));
        // 继承自 ResultMap：原有列映射不丢
        assertTrue(listResultMap.getResultMappings().stream()
                .anyMatch(m -> "alias_".equals(m.getColumn()) && "alias".equals(m.getProperty())));
    }
}
