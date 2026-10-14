package com.ujcms.cms.core.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.ParameterMapping;
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
 * BlockMapper 语句形状测试（docs/spec/console-master-detail）：区块项数子查询只在给出站点时出现，且限定该站点——
 * 全局区块的区块项分属各站，“区块管理”左侧显示的是本站的数量；文章列表的“推荐到区块”、指令、种子导出等
 * 其余调用方传 {@code null}，不付这笔代价。
 *
 * <p>仅解析真实 BlockMapper.xml 并断言 BoundSql（不连数据库）：selectAll 同时服务多个调用方，XML 写坏或
 * {@code @Param} 名与 {@code <if test>} 对不上，会让它们在运行时一起失败，须在这里先暴露。
 */
class BlockMapperItemCountTest {
    private static final String NS = "com.ujcms.cms.core.mapper.BlockMapper.";

    private static Configuration configuration;

    @BeforeAll
    static void setUp() throws Exception {
        configuration = new Configuration();
        // SeqMapper.xml 提供 selectAll 依赖的 Select_All 片段；ResultMap 的关联查询引用 ModelMapper.select，须一并解析
        for (String resource : new String[]{"com/ujcms/cms/core/mapper/SeqMapper.xml",
                "com/ujcms/cms/core/mapper/ModelMapper.xml",
                "com/ujcms/cms/core/mapper/BlockMapper.xml"}) {
            try (InputStream in = Resources.getResourceAsStream(resource)) {
                new XMLMapperBuilder(in, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
    }

    private static BoundSql selectAll(Long itemCountSiteId) {
        Map<String, Object> params = new HashMap<>(4);
        params.put("queryInfo", null);
        params.put("itemCountSiteId", itemCountSiteId);
        return configuration.getMappedStatement(NS + "selectAll").getBoundSql(params);
    }

    @Test
    void itemCountSubqueryIsScopedToTheGivenSite() {
        BoundSql counted = selectAll(1L);
        String sql = counted.getSql();
        assertTrue(sql.contains("from ujcms_block_item t_item_ where t_item_.block_id_ = t.id_ and t_item_.site_id_ = ?"), sql);
        assertTrue(sql.contains("as item_count_"), sql);
        assertTrue(counted.getParameterMappings().stream().map(ParameterMapping::getProperty)
                .anyMatch("itemCountSiteId"::equals));
    }

    @Test
    void noSubqueryWithoutASite() {
        BoundSql plain = selectAll(null);
        assertFalse(plain.getSql().contains("item_count_"));
        assertTrue(plain.getParameterMappings().isEmpty());
    }

    @Test
    void listResultMapCarriesItemCountAndKeepsTheModelAssociation() {
        assertEquals(NS + "ListResultMap", configuration.getMappedStatement(NS + "selectAll").getResultMaps().get(0).getId());
        ResultMap listResultMap = configuration.getResultMap(NS + "ListResultMap");
        assertTrue(listResultMap.getResultMappings().stream()
                .anyMatch(m -> "item_count_".equals(m.getColumn()) && "itemCount".equals(m.getProperty())));
        // 继承自 ResultMap：区块项模型的关联查询不丢（区块项表单据此取字段定义）
        assertTrue(listResultMap.getResultMappings().stream().anyMatch(m -> "blockItemModel".equals(m.getProperty())));
    }
}
