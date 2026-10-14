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
 * FormTypeMapper 语句形状测试（docs/spec/console-master-detail）：表单数子查询只在要求统计时出现，且不按状态过滤——
 * 删除表单类型的守卫 {@code FormMapper.existsByTypeId} 同样不看状态，计数为 0 才等于“可删除”。
 *
 * <p>仅解析真实 FormTypeMapper.xml 并断言 BoundSql 文本（不连数据库）：XML 写坏、selectAll 指向未定义的 resultMap、
 * {@code @Param} 名与 {@code <if test>} 对不上，都会在这里而不是在启动后暴露。
 */
class FormTypeMapperFormCountTest {
    private static final String NS = "com.ujcms.cms.ext.mapper.FormTypeMapper.";

    private static Configuration configuration;

    @BeforeAll
    static void setUp() throws Exception {
        configuration = new Configuration();
        // SeqMapper.xml 提供 selectAll 依赖的 Select_All 片段，须先解析
        for (String resource : new String[]{"com/ujcms/cms/core/mapper/SeqMapper.xml",
                "com/ujcms/cms/ext/mapper/FormTypeMapper.xml"}) {
            try (InputStream in = Resources.getResourceAsStream(resource)) {
                new XMLMapperBuilder(in, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
    }

    private static String selectAllSql(boolean isQueryFormCount) {
        Map<String, Object> params = new HashMap<>(4);
        params.put("queryInfo", null);
        params.put("isQueryFormCount", isQueryFormCount);
        return configuration.getMappedStatement(NS + "selectAll").getBoundSql(params).getSql();
    }

    @Test
    void formCountSubqueryOnlyWhenRequestedAndIgnoresStatus() {
        String counted = selectAllSql(true);
        assertTrue(counted.contains("(select count(*) from ujcms_form t_form_ where t_form_.type_id_ = t.id_) as form_count_"), counted);
        assertFalse(counted.contains("status_"), counted);

        assertFalse(selectAllSql(false).contains("form_count_"));
    }

    @Test
    void listResultMapCarriesFormCountAndKeepsTheModelAssociation() {
        assertEquals(NS + "ListResultMap", configuration.getMappedStatement(NS + "selectAll").getResultMaps().get(0).getId());
        ResultMap listResultMap = configuration.getResultMap(NS + "ListResultMap");
        assertTrue(listResultMap.getResultMappings().stream()
                .anyMatch(m -> "form_count_".equals(m.getColumn()) && "formCount".equals(m.getProperty())));
        // 继承自 ResultMap：表单模型的关联查询不丢（表单管理据此生成各类型的列与查询项）
        assertTrue(listResultMap.getResultMappings().stream().anyMatch(m -> "model".equals(m.getProperty())));
    }
}
